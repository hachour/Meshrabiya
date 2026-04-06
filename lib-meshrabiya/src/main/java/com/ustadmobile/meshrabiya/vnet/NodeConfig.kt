package com.ustadmobile.meshrabiya.vnet

/**
 * Configuration parameters for a [VirtualNode] in the mesh network.
 *
 * This class controls the behavior of routing, discovery, and port allocation
 * for mesh nodes. Use [DEFAULT_CONFIG] as a starting point and override
 * specific values as needed.
 *
 * @property maxHops Maximum number of hops a packet can traverse before being dropped.
 *                   This prevents infinite packet loops. Default is 5.
 * @property originatingMessageInterval Interval in milliseconds between originator message
 *                                     broadcasts. Originator messages are used for route
 *                                     discovery (similar to BATMAN OGM messages). Default is 3000ms.
 * @property originatingMessageInitialDelay Delay before the first originator message is sent
 *                                         after node initialization. Default is 1000ms.
 * @property pingInterval Interval in milliseconds between ping messages to neighbors.
 *                        Used for link quality estimation and keepalive. Default is 5000ms.
 * @property pingTimeout Timeout in milliseconds before a ping is considered failed.
 *                       Default is 3000ms.
 * @property routeTimeoutMs Time in milliseconds after which a route is considered stale
 *                          if no originator message has been received. Default is 15000ms.
 * @property portAllocationMaxAttempts Maximum number of attempts to allocate a random
 *                                     UDP port before giving up. Default is 1000.
 * @property portRangeStart Start of the port range for random allocation (inclusive).
 *                         Default is 1024 (avoid well-known ports).
 * @property portRangeEnd End of the port range for random allocation (exclusive).
 *                       Default is 32768 (below ephemeral range).
 * @property broadcastDedupWindowMs Time window for deduplication of broadcast packets.
 *                                 Packets received within this window with the same
 *                                 originator/sequence are dropped. Default is 5000ms.
 *
 * @see NodeCompanion
 */
data class NodeConfig(
    val maxHops: Int = DEFAULT_MAX_HOPS,
    val originatingMessageInterval: Long = DEFAULT_ORIGINATING_MESSAGE_INTERVAL,
    val originatingMessageInitialDelay: Long = DEFAULT_ORIGINATING_MESSAGE_INITIAL_DELAY,
    val pingInterval: Long = DEFAULT_PING_INTERVAL,
    val pingTimeout: Long = DEFAULT_PING_TIMEOUT,
    val routeTimeoutMs: Long = DEFAULT_ROUTE_TIMEOUT_MS,
    val portAllocationMaxAttempts: Int = DEFAULT_PORT_ALLOCATION_MAX_ATTEMPTS,
    val portRangeStart: Int = DEFAULT_PORT_RANGE_START,
    val portRangeEnd: Int = DEFAULT_PORT_RANGE_END,
    val broadcastDedupWindowMs: Long = DEFAULT_BROADCAST_DEDUP_WINDOW_MS,
) {

    companion object {
        /** Default maximum number of hops for packet forwarding */
        const val DEFAULT_MAX_HOPS = 5

        /** Default interval between originator message broadcasts (ms) */
        const val DEFAULT_ORIGINATING_MESSAGE_INTERVAL = 3000L

        /** Default delay before first originator message (ms) */
        const val DEFAULT_ORIGINATING_MESSAGE_INITIAL_DELAY = 1000L

        /** Default interval between ping messages to neighbors (ms) */
        const val DEFAULT_PING_INTERVAL = 5000L

        /** Default timeout for ping responses (ms) */
        const val DEFAULT_PING_TIMEOUT = 3000L

        /** Default timeout before a route is considered stale (ms) */
        const val DEFAULT_ROUTE_TIMEOUT_MS = 15000L

        /** Default maximum attempts for port allocation */
        const val DEFAULT_PORT_ALLOCATION_MAX_ATTEMPTS = 1000

        /** Default start of random port range (above well-known ports) */
        const val DEFAULT_PORT_RANGE_START = 1024

        /** Default end of random port range (below ephemeral range) */
        const val DEFAULT_PORT_RANGE_END = 32768

        /** Default broadcast deduplication window (ms) */
        const val DEFAULT_BROADCAST_DEDUP_WINDOW_MS = 5000L

        /**
         * Default configuration suitable for most mesh networks.
         * Provides a balance between discovery speed and network overhead.
         */
        val DEFAULT_CONFIG = NodeConfig()

        /**
         * Configuration optimized for smaller networks with low latency requirements.
         * Uses shorter intervals and fewer hops.
         */
        val LOW_LATENCY_CONFIG = NodeConfig(
            maxHops = 3,
            originatingMessageInterval = 1500L,
            originatingMessageInitialDelay = 500L,
            pingInterval = 2000L,
            pingTimeout = 1000L,
            routeTimeoutMs = 5000L,
        )

        /**
         * Configuration optimized for larger networks with many hops.
         * Uses longer intervals to reduce network overhead.
         */
        val LARGE_NETWORK_CONFIG = NodeConfig(
            maxHops = 10,
            originatingMessageInterval = 5000L,
            originatingMessageInitialDelay = 2000L,
            pingInterval = 10000L,
            pingTimeout = 5000L,
            routeTimeoutMs = 30000L,
        )
    }

    /**
     * Validates that all configuration values are within acceptable ranges.
     *
     * @throws IllegalArgumentException if any value is invalid
     */
    fun validate() {
        require(maxHops in 1..15) { "maxHops must be between 1 and 15, got $maxHops" }
        require(originatingMessageInterval > 0) { "originatingMessageInterval must be positive" }
        require(originatingMessageInitialDelay >= 0) { "originatingMessageInitialDelay must be non-negative" }
        require(pingInterval > 0) { "pingInterval must be positive" }
        require(pingTimeout > 0) { "pingTimeout must be positive" }
        require(pingTimeout < pingInterval) { "pingTimeout must be less than pingInterval" }
        require(routeTimeoutMs > originatingMessageInterval * 2) { 
            "routeTimeoutMs should be greater than 2x originatingMessageInterval" 
        }
        require(portAllocationMaxAttempts in 10..10000) { 
            "portAllocationMaxAttempts must be between 10 and 10000" 
        }
        require(portRangeStart in 1024..49151) { 
            "portRangeStart must be between 1024 and 49151" 
        }
        require(portRangeEnd in portRangeStart..65535) { 
            "portRangeEnd must be >= portRangeStart and <= 65535" 
        }
        require(broadcastDedupWindowMs >= 0) { 
            "broadcastDedupWindowMs must be non-negative" 
        }
    }
}
