package com.ustadmobile.meshrabiya.vnet

/**
 * Meshrabiya - Android Mesh Networking Library
 *
 * Copyright (c) 2024 UstadMobile. All rights reserved.
 *
 * This library provides mesh networking capabilities for Android devices using WiFi Direct
 * and Local Only Hotspots. It enables multi-hop communication between devices without
 * requiring a WiFi access point.
 */

import android.util.Log
import com.ustadmobile.meshrabiya.log.MNetLoggerStdout
import com.ustadmobile.meshrabiya.ext.addressToByteArray
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.ext.prefixMatches
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.ustadmobile.meshrabiya.log.MNetLogger
import com.ustadmobile.meshrabiya.mmcp.MmcpHotspotRequest
import com.ustadmobile.meshrabiya.mmcp.MmcpHotspotResponse
import com.ustadmobile.meshrabiya.mmcp.MmcpMessage
import com.ustadmobile.meshrabiya.mmcp.MmcpMessageAndPacketHeader
import com.ustadmobile.meshrabiya.mmcp.MmcpOriginatorMessage
import com.ustadmobile.meshrabiya.mmcp.MmcpPing
import com.ustadmobile.meshrabiya.mmcp.MmcpPong
import com.ustadmobile.meshrabiya.portforward.ForwardBindPoint
import com.ustadmobile.meshrabiya.portforward.UdpForwardRule
import com.ustadmobile.meshrabiya.util.findFreePort
import com.ustadmobile.meshrabiya.vnet.VirtualPacket.Companion.ADDR_BROADCAST
import com.ustadmobile.meshrabiya.vnet.bluetooth.MeshrabiyaBluetoothState
import com.ustadmobile.meshrabiya.vnet.datagram.VirtualDatagramSocket2
import com.ustadmobile.meshrabiya.vnet.datagram.VirtualDatagramSocketImpl
import com.ustadmobile.meshrabiya.vnet.socket.ChainSocketFactory
import com.ustadmobile.meshrabiya.vnet.socket.ChainSocketFactoryImpl
import com.ustadmobile.meshrabiya.vnet.socket.ChainSocketNextHop
import com.ustadmobile.meshrabiya.vnet.socket.ChainSocketServer
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.HotspotType
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig
import com.ustadmobile.meshrabiya.vnet.wifi.MeshrabiyaWifiManager
import com.ustadmobile.meshrabiya.vnet.wifi.LocalHotspotRequest
import com.ustadmobile.meshrabiya.vnet.wifi.LocalHotspotResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlin.random.Random

/**
 * Generates a random Automatic Private IP Address (APIPA) in the 169.254.x.x range.
 * 
 * APIPA addresses are used for link-local communication and are automatically
 * assigned when no DHCP server is available. In Meshrabiya, each node gets a
 * unique APIPA address for identification on the virtual mesh network.
 * 
 * @return A 32-bit integer representing the APIPA address
 */
fun randomApipaAddr(): Int {
    //169.254
    val fixedSection = (169 shl 24).or(254 shl 16)

    val randomSection = Random.nextInt(Short.MAX_VALUE.toInt())

    return fixedSection.or(randomSection)
}

/**
 * Creates a random APIPA [InetAddress] for use as a virtual node address.
 * 
 * @return An [InetAddress] in the 169.254.x.x range
 */
fun randomApipaInetAddr() = InetAddress.getByAddress(randomApipaAddr().addressToByteArray())

/**
 * VirtualNode is the core abstraction representing a node in the Meshrabiya mesh network.
 *
 * A VirtualNode provides:
 * - Virtual IP addressing using APIPA addresses (169.254.x.x)
 * - Multi-hop routing using BATMAN-style originator message propagation
 * - TCP socket factory for transparent multi-hop connections
 * - UDP datagram socket support with broadcast capability
 * - Integration with WiFi Direct Groups and Local Only Hotspots
 *
 * ## Architecture Overview
 *
 * The mesh network operates on these principles:
 * 1. Each node has a virtual IP address and can create/connect to hotspots
 * 2. Nodes discover each other through originator message broadcasting
 * 3. Packets are routed hop-by-hop using the chain socket mechanism
 * 4. Both WiFi Direct Groups and Local Only Hotspots are supported
 *
 * ## Connection Types
 *
 * Connections refer to the underlying "real" connections to other devices:
 * - WiFi Direct Groups (supports concurrent STA+P2P on most devices)
 * - Local Only Hotspots (requires STA+AP concurrency support on Android 11+)
 * - Bluetooth (for discovery and low-bandwidth fallback)
 *
 * ## Addressing
 *
 * Addresses are 32-bit integers in the APIPA range (169.254.0.0/16). This avoids
 * conflicts with common private network ranges and requires no coordination.
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Create a virtual node
 * val node = AndroidVirtualNode(
 *     appContext = context,
 *     dataStore = dataStore,
 *     address = randomApipaInetAddr()
 * )
 *
 * // Create a hotspot for others to connect
 * node.setWifiHotspotEnabled(enabled = true, preferredBand = ConnectBand.BAND_5GHZ)
 *
 * // Use with OkHttp for transparent mesh communication
 * val client = OkHttpClient.Builder()
 *     .socketFactory(node.socketFactory)
 *     .build()
 * ```
 *
 * @property port The port number for the virtual node (0 for auto-assignment)
 * @property json JSON serializer for mesh control protocol messages
 * @property logger Logging interface for debug output
 * @property address The virtual IP address of this node
 * @property networkPrefixLength The network prefix length (default 16 for APIPA)
 * @property config Configuration parameters for the node behavior
 *
 * @see AndroidVirtualNode for the Android-specific implementation
 * @see MeshrabiyaConnectLink for connection sharing between nodes
 */
abstract class VirtualNode(
    val port: Int = 0,
    val json: Json = Json,
    val logger: MNetLogger = MNetLoggerStdout(),
    final override val address: InetAddress = randomApipaInetAddr(),
    override val networkPrefixLength: Int = 16,
    val config: NodeConfig = NodeConfig.DEFAULT_CONFIG,
): VirtualRouter, Closeable {

    val addressAsInt: Int = address.requireAddressAsInt()

    //This executor is used for direct I/O activities
    protected val connectionExecutor: ExecutorService = Executors.newCachedThreadPool()

    //This executor is used to schedule maintenance e.g. pings etc.
    protected val scheduledExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

    protected val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    private val mmcpMessageIdAtomic = AtomicInteger()

    protected val _state = MutableStateFlow(LocalNodeState())

    val state: Flow<LocalNodeState> = _state.asStateFlow()

    abstract val meshrabiyaWifiManager: MeshrabiyaWifiManager

    private val pongListeners = CopyOnWriteArrayList<PongListener>()

    protected val logPrefix: String = "[VirtualNode ${addressAsInt.addressToDotNotation()}]"

    protected val iDatagramSocketFactory = VirtualNodeReturnPathSocketFactory(this)

    private val forwardingRules: MutableMap<ForwardBindPoint, UdpForwardRule> = ConcurrentHashMap()

    /**
     * @param originatorMessage the Originator message itself
     * @param timeReceived the time this message was received
     * @param lastHopAddr the recorded last hop address
     */
    data class LastOriginatorMessage(
        val originatorMessage: MmcpOriginatorMessage,
        val timeReceived: Long,
        val lastHopAddr: Int,
        val hopCount: Byte,
        val lastHopRealInetAddr: InetAddress,
        val receivedFromSocket: VirtualNodeDatagramSocket,
        val lastHopRealPort: Int,
    )

    @Suppress("unused") //Part of the API
    enum class Zone {
        VNET, REAL
    }

    private val originatingMessageManager = OriginatingMessageManager(
        localNodeInetAddr = address,
        logger = logger,
        scheduledExecutorService = scheduledExecutor,
        nextMmcpMessageId = this::nextMmcpMessageId,
        getWifiState = { _state.value.wifiState },
    )

    private val localPort = findFreePort(0)

    val datagramSocket = VirtualNodeDatagramSocket(
        socket = DatagramSocket(localPort),
        ioExecutorService = connectionExecutor,
        router = this,
        localNodeVirtualAddress = addressAsInt,
        logger = logger,
    )

    protected val chainSocketFactory: ChainSocketFactory = ChainSocketFactoryImpl(
        virtualRouter = this,
        logger = logger,
    )

    val socketFactory: SocketFactory
        get() = chainSocketFactory

    private val chainSocketServer = ChainSocketServer(
        serverSocket = ServerSocket(localPort),
        executorService = connectionExecutor,
        chainSocketFactory = chainSocketFactory,
        name = addressAsInt.addressToDotNotation(),
        logger = logger
    )

    private val _incomingMmcpMessages = MutableSharedFlow<MmcpMessageAndPacketHeader>(
        replay = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val incomingMmcpMessages: Flow<MmcpMessageAndPacketHeader> = _incomingMmcpMessages.asSharedFlow()

    private val activeSockets: MutableMap<Int, VirtualDatagramSocketImpl> = ConcurrentHashMap()

    init {
        _state.update { prev ->
            prev.copy(
                address = addressAsInt,
                connectUri = generateConnectLink(hotspot = null).uri
            )
        }

        coroutineScope.launch {
            originatingMessageManager.state.collect {
                _state.update { prev ->
                    prev.copy(
                        originatorMessages = it
                    )
                }
            }
        }
    }

    override fun nextMmcpMessageId() = mmcpMessageIdAtomic.incrementAndGet()


    /**
     * Allocates a UDP port for virtual datagram socket use.
     *
     * If a specific port is requested (portNum > 0) and available, it will be allocated.
     * Otherwise, a random port is selected from the configured range.
     *
     * @param virtualDatagramSocketImpl The socket implementation to associate with
     * @param portNum The requested port number (0 for random allocation)
     * @return The allocated port number
     * @throws IllegalStateException if no port can be allocated
     */
    override fun allocateUdpPortOrThrow(
        virtualDatagramSocketImpl: VirtualDatagramSocketImpl,
        portNum: Int
    ): Int {
        if(portNum > 0) {
            if(activeSockets.containsKey(portNum))
                throw IllegalStateException("VirtualNode: port $portNum already allocated!")

            //requested port is not allocated, everything OK
            activeSockets[portNum] = virtualDatagramSocketImpl
            return portNum
        }

        // Use configured port range instead of hardcoded values
        val portStart = config.portRangeStart
        val portEnd = config.portRangeEnd
        val maxAttempts = config.portAllocationMaxAttempts

        var attemptCount = 0
        do {
            val randomPort = Random.nextInt(portStart, portEnd)
            if(!activeSockets.containsKey(randomPort)) {
                activeSockets[randomPort] = virtualDatagramSocketImpl
                return randomPort
            }

            attemptCount++
        } while (attemptCount < maxAttempts)

        throw IllegalStateException(
            "Could not allocate random free port after $maxAttempts attempts. " +
            "Active sockets: ${activeSockets.size}"
        )
    }

    override fun deallocatePort(protocol: Protocol, portNum: Int) {
        activeSockets.remove(portNum)
    }

    fun createDatagramSocket(): DatagramSocket {
        return VirtualDatagramSocket2(this, addressAsInt, logger)
    }

    fun createBoundDatagramSocket(port: Int): DatagramSocket {
        return createDatagramSocket().also {
            it.bind(InetSocketAddress(address, port))
        }
    }

    /**
     *
     */
    fun forward(
        bindAddress: InetAddress,
        bindPort: Int,
        destAddress: InetAddress,
        destPort: Int,
    ) : Int {
        val listenSocket = if(
            bindAddress.prefixMatches(networkPrefixLength, address)
        ) {
            createBoundDatagramSocket(bindPort)
        }else {
            DatagramSocket(bindPort, bindAddress)
        }

        val forwardRule = createForwardRule(listenSocket, destAddress, destPort)
        val boundPort = listenSocket.localPort
        forwardingRules[ForwardBindPoint(bindAddress, null, boundPort)] = forwardRule

        return boundPort
    }

    fun forward(
        bindZone: Zone,
        bindPort: Int,
        destAddress: InetAddress,
        destPort: Int
    ): Int {
        val listenSocket = if(bindZone == Zone.VNET) {
            createBoundDatagramSocket(bindPort)
        }else {
            DatagramSocket(bindPort)
        }
        val forwardRule = createForwardRule(listenSocket, destAddress, destPort)
        val boundPort = listenSocket.localPort
        forwardingRules[ForwardBindPoint(null, bindZone, boundPort)] = forwardRule
        return boundPort
    }

    /**
     * Stop a UDP forwarding rule that was bound by zone.
     *
     * @param bindZone The zone (VNET or REAL) where the socket is bound
     * @param bindPort The port number to stop forwarding on
     * @return true if a forwarding rule was found and removed, false otherwise
     */
    fun stopForward(
        bindZone: Zone,
        bindPort: Int
    ): Boolean {
        val key = ForwardBindPoint(null, bindZone, bindPort)
        val rule = forwardingRules.remove(key)
        return if (rule != null) {
            try {
                rule.boundSocket.close()
                logger(Log.INFO, "$logPrefix: stopForward: Stopped forwarding rule for $bindZone:$bindPort")
                true
            } catch (e: Exception) {
                logger(Log.WARN, "$logPrefix: stopForward: Error closing socket for $bindZone:$bindPort", e)
                false
            }
        } else {
            logger(Log.DEBUG, "$logPrefix: stopForward: No forwarding rule found for $bindZone:$bindPort")
            false
        }
    }

    /**
     * Stop a UDP forwarding rule that was bound by address.
     *
     * @param bindAddr The address where the socket is bound
     * @param bindPort The port number to stop forwarding on
     * @return true if a forwarding rule was found and removed, false otherwise
     */
    fun stopForward(
        bindAddr: InetAddress,
        bindPort: Int,
    ): Boolean {
        val key = ForwardBindPoint(bindAddr, null, bindPort)
        val rule = forwardingRules.remove(key)
        return if (rule != null) {
            try {
                rule.boundSocket.close()
                logger(Log.INFO, "$logPrefix: stopForward: Stopped forwarding rule for ${bindAddr.hostAddress}:$bindPort")
                true
            } catch (e: Exception) {
                logger(Log.WARN, "$logPrefix: stopForward: Error closing socket for ${bindAddr.hostAddress}:$bindPort", e)
                false
            }
        } else {
            logger(Log.DEBUG, "$logPrefix: stopForward: No forwarding rule found for ${bindAddr.hostAddress}:$bindPort")
            false
        }
    }

    private fun createForwardRule(
        listenSocket: DatagramSocket,
        destAddress: InetAddress,
        destPort: Int,
    ) : UdpForwardRule {
        return UdpForwardRule(
            boundSocket = listenSocket,
            ioExecutor = this.connectionExecutor,
            destAddress = destAddress,
            destPort = destPort,
            logger = logger,
            returnPathSocketFactory = iDatagramSocketFactory,
        )
    }


    override val localDatagramPort: Int
        get() = datagramSocket.localPort


    protected fun generateConnectLink(
        hotspot: WifiConnectConfig?,
        bluetoothConfig: MeshrabiyaBluetoothState? = null,
    ) : MeshrabiyaConnectLink {
        return MeshrabiyaConnectLink.fromComponents(
            nodeAddr = addressAsInt,
            port = localDatagramPort,
            hotspotConfig = hotspot,
            bluetoothConfig = bluetoothConfig,
            json = json,
        )
    }

    private fun onIncomingMmcpMessage(
        virtualPacket: VirtualPacket,
        datagramPacket: DatagramPacket?,
        datagramSocket: VirtualNodeDatagramSocket?,
    ) : Boolean {
        //This is an Mmcp message
        try {
            val mmcpMessage = MmcpMessage.fromVirtualPacket(virtualPacket)
            val from = virtualPacket.header.fromAddr
            logger(Log.VERBOSE,
                message = {
                    "$logPrefix received MMCP message (${mmcpMessage::class.simpleName}) " +
                    "from ${from.addressToDotNotation()}"
                }
            )

            val isToThisNode = virtualPacket.header.toAddr == addressAsInt

            var shouldRoute = true

            when {
                mmcpMessage is MmcpPing && isToThisNode -> {
                    logger(Log.VERBOSE,
                        message = {
                            "$logPrefix Received ping(id=${mmcpMessage.messageId}) from ${from.addressToDotNotation()}"
                        }
                    )
                    //send pong
                    val pongMessage = MmcpPong(
                        messageId = nextMmcpMessageId(),
                        replyToMessageId = mmcpMessage.messageId
                    )

                    val replyPacket = pongMessage.toVirtualPacket(
                        toAddr = from,
                        fromAddr = addressAsInt
                    )

                    logger(Log.VERBOSE, { "$logPrefix Sending pong to ${from.addressToDotNotation()}" })
                    route(replyPacket)
                }

                mmcpMessage is MmcpPong && isToThisNode -> {
                    logger(Log.VERBOSE, { "$logPrefix Received pong(id=${mmcpMessage.messageId})}" })
                    originatingMessageManager.onPongReceived(from, mmcpMessage)
                    pongListeners.forEach {
                        it.onPongReceived(from, mmcpMessage)
                    }
                }

                mmcpMessage is MmcpHotspotRequest && isToThisNode -> {
                    logger(Log.INFO, "$logPrefix Received hotspotrequest (id=${mmcpMessage.messageId})", null)
                    coroutineScope.launch {
                        val hotspotResult = meshrabiyaWifiManager.requestHotspot(
                            mmcpMessage.messageId, mmcpMessage.hotspotRequest
                        )

                        if(from != addressAsInt) {
                            val replyPacket = MmcpHotspotResponse(
                                messageId = mmcpMessage.messageId,
                                result = hotspotResult
                            ).toVirtualPacket(
                                toAddr = from,
                                fromAddr = addressAsInt
                            )
                            logger(Log.INFO, "$logPrefix sending hotspotresponse to ${from.addressToDotNotation()}", null)
                            route(replyPacket)
                        }
                    }
                }

                mmcpMessage is MmcpOriginatorMessage -> {
                    shouldRoute = originatingMessageManager.onReceiveOriginatingMessage(
                        mmcpMessage = mmcpMessage,
                        datagramPacket = datagramPacket ?: return false,
                        datagramSocket = datagramSocket ?: return false,
                        virtualPacket = virtualPacket,
                    )
                }

                else -> {
                    // do nothing
                }
            }

            _incomingMmcpMessages.tryEmit(MmcpMessageAndPacketHeader(mmcpMessage, virtualPacket.header))

            return shouldRoute
        }catch(e: Exception) {
            e.printStackTrace()
            return false
        }

    }


    /**
     * Routes a virtual packet to its destination.
     *
     * This method handles both unicast and broadcast packets. For unicast
     * packets, it looks up the next hop using the originator message table.
     * For broadcast packets, it forwards to all neighbors except the sender.
     *
     * Important: This method catches and logs all exceptions to prevent
     * the routing loop from crashing. Failed routing attempts are logged
     * but do not propagate exceptions.
     *
     * @param packet The virtual packet to route
     * @param datagramPacket The underlying datagram packet (if available)
     * @param virtualNodeDatagramSocket The socket to use for forwarding
     */
    override fun route(
        packet: VirtualPacket,
        datagramPacket: DatagramPacket?,
        virtualNodeDatagramSocket: VirtualNodeDatagramSocket?
    ) {
        try {
            val fromLastHop = packet.header.lastHopAddr

            if(packet.header.hopCount >= config.maxHops) {
                logger(Log.DEBUG,
                    "Drop packet from ${packet.header.fromAddr.addressToDotNotation()} - " +
                            "${packet.header.hopCount} exceeds ${config.maxHops}",
                    null)
                return
            }

            if(packet.header.toPort == 0 && packet.header.fromAddr != addressAsInt){
                //this is an MMCP message
                if(!onIncomingMmcpMessage(packet, datagramPacket, virtualNodeDatagramSocket)){
                    //It was determined that this packet should go no further by MMCP processing
                    logger(Log.DEBUG, "Drop mmcp packet from ${packet.header.fromAddr}", null)
                }
            }

            if(packet.header.toAddr == addressAsInt) {
                //this is an incoming packet - give to the destination virtual socket/forwarding
                val listeningSocket = activeSockets[packet.header.toPort]
                if(listeningSocket != null) {
                    listeningSocket.onIncomingPacket(packet)
                }else {
                    logger(Log.DEBUG, "$logPrefix Incoming packet received, but no socket listening on: ${packet.header.toPort}")
                }
            }else {
                //packet needs to be sent to next hop / destination
                val toAddr = packet.header.toAddr

                packet.updateLastHopAddrAndIncrementHopCountInData(addressAsInt)
                if(toAddr == ADDR_BROADCAST) {
                    originatingMessageManager.neighbors().filter {
                        it.first != fromLastHop && it.first != packet.header.fromAddr
                    }.forEach {
                        logger(Log.VERBOSE,
                            message = {
                                "$logPrefix broadcast packet " +
                                        "from=${packet.header.fromAddr.addressToDotNotation()} " +
                                        "lasthop=${fromLastHop.addressToDotNotation()} " +
                                        "send to ${it.first.addressToDotNotation()}"
                            }
                        )

                        try {
                            it.second.receivedFromSocket.send(
                                nextHopAddress = it.second.lastHopRealInetAddr,
                                nextHopPort = it.second.lastHopRealPort,
                                virtualPacket = packet,
                            )
                        } catch (e: Exception) {
                            logger(Log.WARN,
                                "$logPrefix Failed to broadcast to ${it.first.addressToDotNotation()}",
                                e
                            )
                        }
                    }

                }else {
                    val originatorMessage = originatingMessageManager
                        .findOriginatingMessageFor(packet.header.toAddr)
                    if(originatorMessage != null) {
                        try {
                            originatorMessage.receivedFromSocket.send(
                                nextHopAddress = originatorMessage.lastHopRealInetAddr,
                                nextHopPort = originatorMessage.lastHopRealPort,
                                virtualPacket = packet
                            )
                        } catch (e: Exception) {
                            logger(Log.WARN,
                                "$logPrefix Failed to send to next hop ${originatorMessage.lastHopRealInetAddr.hostAddress}",
                                e
                            )
                        }
                    }else {
                        logger(Log.WARN, "$logPrefix route: Cannot route packet to " +
                                "${packet.header.toAddr.addressToDotNotation()} : no known nexthop")
                    }
                }
            }
        }catch(e: Exception) {
            // Log but do not re-throw - this prevents the routing loop from crashing
            // due to a single packet processing error
            logger(Log.ERROR,
                "$logPrefix : route : exception routing packet from ${packet.header.fromAddr.addressToDotNotation()}",
                e
            )
        }
    }

    override fun lookupNextHopForChainSocket(address: InetAddress, port: Int): ChainSocketNextHop {
        return originatingMessageManager.lookupNextHopForChainSocket(address, port)
    }


    /**
     * Respond to a new
     */
    fun addNewNeighborConnection(
        address: InetAddress,
        port: Int,
        neighborNodeVirtualAddr: Int,
        socket: VirtualNodeDatagramSocket,
    ) {
        logger(Log.DEBUG,
            "$logPrefix addNewNeighborConnection connection to virtual addr " +
                    "${neighborNodeVirtualAddr.addressToDotNotation()} " +
                    "via datagram to $address:$port",
            null
        )

        coroutineScope.launch {
            originatingMessageManager.addNeighbor(
                neighborRealInetAddr = address,
                neighborRealPort = port,
                socket =  socket,
            )
        }

    }

    fun addPongListener(listener: PongListener) {
        pongListeners += listener
    }

    fun removePongListener(listener: PongListener) {
        pongListeners -= listener
    }

    open suspend fun setWifiHotspotEnabled(
        enabled: Boolean,
        preferredBand: ConnectBand = ConnectBand.BAND_2GHZ,
        hotspotType: HotspotType = HotspotType.AUTO,
    ): LocalHotspotResponse? {
        return if(enabled){
             meshrabiyaWifiManager.requestHotspot(
                requestMessageId = nextMmcpMessageId(),
                request = LocalHotspotRequest(
                    preferredBand = preferredBand,
                    preferredType = hotspotType,
                )
            )
        }else {
            meshrabiyaWifiManager.deactivateHotspot()
            LocalHotspotResponse(
                responseToMessageId = 0,
                config = null,
                errorCode = 0,
                redirectAddr = 0,
            )
        }
    }

    override fun close() {
        datagramSocket.close(closeSocket = true)
        chainSocketServer.close(closeSocket = true)
        coroutineScope.cancel(message = "VirtualNode closed")

        connectionExecutor.shutdown()
        scheduledExecutor.shutdown()
    }

}