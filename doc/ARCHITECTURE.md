# Meshrabiya Architecture Documentation

This document provides a comprehensive overview of the Meshrabiya mesh networking library architecture.

## Table of Contents

1. [Overview](#overview)
2. [Core Components](#core-components)
3. [Network Topology](#network-topology)
4. [Protocol Stack](#protocol-stack)
5. [Data Flow](#data-flow)
6. [WiFi Integration](#wifi-integration)
7. [Routing Mechanism](#routing-mechanism)
8. [Security Considerations](#security-considerations)

---

## Overview

Meshrabiya creates a virtual network layer over WiFi Direct and Local Only Hotspots, enabling multi-hop communication between Android devices without infrastructure.

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│  (OkHttp, custom TCP/UDP sockets, HTTP servers, etc.)       │
├─────────────────────────────────────────────────────────────┤
│                    Virtual Network Layer                     │
│  ┌──────────────────┐  ┌──────────────────────────────────┐ │
│  │   SocketFactory  │  │    DatagramSocket (UDP)          │ │
│  │      (TCP)       │  │                                  │ │
│  └────────┬─────────┘  └─────────────────┬────────────────┘ │
│           │                              │                  │
│  ┌────────▼──────────────────────────────▼────────────────┐ │
│  │              VirtualNode (Routing)                      │ │
│  │   - Virtual IP addressing (169.254.x.x)                │ │
│  │   - Originator message management                       │ │
│  │   - Chain socket routing                                │ │
│  └─────────────────────────┬───────────────────────────────┘ │
├─────────────────────────────┼───────────────────────────────┤
│     Transport Layer         │                               │
│  ┌──────────────────────────▼──────────────────────────────┐│
│  │              MMCP (Mesh Control Protocol)               ││
│  │   - Ping/Pong (keepalive)                               ││
│  │   - Originator messages (route discovery)               ││
│  │   - Hotspot requests                                    ││
│  └─────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────┤
│              Physical Layer (WiFi / Bluetooth)               │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │ WiFi Direct     │  │ Local Only      │                   │
│  │ Group           │  │ Hotspot         │                   │
│  └─────────────────┘  └─────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Components

### VirtualNode

The central abstraction representing a node in the mesh network.

**Key Responsibilities:**
- Virtual IP address management (APIPA 169.254.x.x range)
- Packet routing and forwarding
- Socket factory provision
- Network state management

**Location:** `lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/vnet/VirtualNode.kt`

### AndroidVirtualNode

Android-specific implementation of VirtualNode.

**Key Responsibilities:**
- WiFi hotspot management
- Bluetooth integration
- Android lifecycle management
- State persistence via DataStore

**Location:** `lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/vnet/AndroidVirtualNode.kt`

### MeshrabiyaWifiManager

Manages WiFi connectivity for the mesh network.

**Key Components:**
- `MeshrabiyaWifiManagerAndroid` - Android implementation
- Support for WiFi Direct Groups
- Support for Local Only Hotspots
- Automatic BSSID storage for seamless reconnection

**Location:** `lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/vnet/wifi/`

### ChainSocket

Implements multi-hop TCP connections through a proxy-chain mechanism.

**How it works:**
1. Client connects to first hop
2. Sends connection request with final destination
3. Each hop forwards to the next
4. Final hop connects to destination port

**Location:** `lib-meshrabiya/src/main/java/com/ustadmobile/meshrabiya/vnet/socket/`

---

## Network Topology

### Node Addressing

Each node receives a virtual IP address in the APIPA range (169.254.0.0/16):

```
169.254.X.Y
     │   └── Random (0-32767)
     └── Fixed (169.254)
```

This ensures:
- No coordination needed between nodes
- No conflict with common private networks (192.168.x.x, 10.x.x.x)
- Automatic link-local addressing

### Hotspot Types

#### WiFi Direct Group
```
┌─────────────────┐
│   Node A (GO)   │ ◄── Creates group
│  169.254.1.1    │
└────────┬────────┘
         │ WiFi Direct
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────┐
│Node B │ │Node C │ ◄── Legacy clients
│ .1.2  │ │ .1.3  │
└───────┘ └───────┘

- Uses IPv6 link-local for group owner discovery
- All nodes get same IPv4 (192.168.49.1) from DHCP
- Works on most Android devices (STA+P2P concurrency)
```

#### Local Only Hotspot
```
┌─────────────────┐
│   Node A (AP)   │ ◄── Creates hotspot
│  169.254.1.1    │
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────┐
│Node B │ │Node C │
│ .1.2  │ │ .1.3  │
└───────┘ └───────┘

- Random subnet assignment by Android
- No IP conflicts
- Requires STA+AP concurrency (Android 11+)
```

---

## Protocol Stack

### MMCP (Mesh Control Protocol)

MMCP is the control protocol used for mesh management.

#### Message Types

| Message | Purpose | Port |
|---------|---------|------|
| `MmcpPing` | Keepalive and latency measurement | 0 (control) |
| `MmcpPong` | Response to ping | 0 (control) |
| `MmcpOriginatorMessage` | Route advertisement | 0 (control) |
| `MmcpHotspotRequest` | Request remote node create hotspot | 0 (control) |
| `MmcpHotspotResponse` | Response with hotspot credentials | 0 (control) |

#### Originator Message Format

Based on [BATMAN Advanced](https://www.open-mesh.org/doc/batman-adv/OGM.html):

```
┌───────────────────────────────────────────────────────┐
│                Originator Message                      │
├───────────────────────────────────────────────────────┤
│ Originator Address (virtual IP)     [4 bytes]         │
│ Sequence Number                     [4 bytes]         │
│ Hop Count                           [1 byte]          │
│ Connect Link URI                    [variable]        │
│   - SSID, Passphrase, BSSID                           │
│   - Service port                                      │
│   - IPv6 link-local (if WiFi Direct)                  │
└───────────────────────────────────────────────────────┘
```

### Virtual Packet Header

```
┌───────────────────────────────────────────────────────┐
│               Virtual Packet Header                    │
├───────────────────────────────────────────────────────┤
│ From Address (virtual IP)          [4 bytes]          │
│ To Address (virtual IP)            [4 bytes]          │
│ From Port                          [2 bytes]          │
│ To Port                            [2 bytes]          │
│ Last Hop Address                   [4 bytes]          │
│ Hop Count                          [1 byte]           │
│ Flags                              [1 byte]           │
└───────────────────────────────────────────────────────┘
```

---

## Data Flow

### TCP Connection Establishment

```
Client (A)                Hop (B)                  Server (C)
    │                        │                         │
    │ ──── Chain Init ────►  │                         │
    │  (dest: C:8080)        │                         │
    │                        │ ──── Chain Init ────►   │
    │                        │  (dest: C:8080)         │
    │                        │                         │
    │                        │ ◄─── Chain Accept ────  │
    │ ◄─── Chain Accept ──── │                         │
    │                        │                         │
    │ ═════ Data Stream ═════╪═══════════════════════► │
    │                        │                         │
```

### UDP Broadcasting

```
Node A                    Node B                   Node C
   │                         │                        │
   │ ─── Broadcast ─────────►│                        │
   │     (255.255.255.255)   │                        │
   │                         │ ─── Forward ──────────►│
   │                         │   (if hop < max)       │
   │                         │                        │
```

---

## WiFi Integration

### Connection Flow

```
┌──────────┐    QR Code/BLE     ┌──────────┐
│  Node A  │ ◄─────────────────►│  Node B  │
│ (Server) │                    │ (Client) │
└────┬─────┘                    └────┬─────┘
     │                               │
     │ 1. Create Hotspot             │
     │◄──────────────────────────────┤
     │                               │
     │ 2. Generate Connect Link      │
     │    (SSID, Pass, BSSID, Port)  │
     ├──────────────────────────────►│
     │                               │
     │                               │ 3. Connect via
     │                               │    WifiNetworkSpecifier
     │                               │
     │ 4. Receive UDP packet         │
     │◄──────────────────────────────┤
     │    (discovers client IP)      │
     │                               │
     │ 5. Exchange Originator Msgs   │
     │◄─────────────────────────────►│
     │                               │
```

### BSSID Storage

On Android 10+, storing the BSSID avoids repeated confirmation dialogs:

```kotlin
// First connection - user confirms
node.connectAsStation(config)

// Store BSSID for future use
node.storeBssid(ssid, bssid)

// Subsequent connections - no dialog needed
// because BSSID is provided
```

---

## Routing Mechanism

### Route Discovery

Based on BATMAN (Better Approach To Mobile Ad-hoc Networking):

1. **Originator Message Broadcast**
   - Each node periodically broadcasts originator messages
   - Messages contain: node address, hop count, connect link
   - Limited by `maxHops` configuration

2. **Route Table Building**
   - Nodes track which neighbor provided the best route to each destination
   - Lower hop count = better route
   - Routes are updated on each originator message receipt

3. **Packet Forwarding**
   - Outgoing packets are sent to the next hop
   - Each hop updates the `lastHopAddr` field
   - Hop count is incremented at each hop

### Route Table Structure

```kotlin
data class LastOriginatorMessage(
    val originatorMessage: MmcpOriginatorMessage,  // The message itself
    val timeReceived: Long,                         // When received
    val lastHopAddr: Int,                          // Who sent it
    val hopCount: Byte,                            // Distance to originator
    val lastHopRealInetAddr: InetAddress,          // Real IP of next hop
    val receivedFromSocket: VirtualNodeDatagramSocket, // Socket to use
    val lastHopRealPort: Int,                      // Port of next hop
)
```

---

## Security Considerations

### Current State

⚠️ **Important**: The current implementation does not include encryption at the mesh layer.

### Recommended Security Measures

1. **Application-Level Encryption**
   - Use TLS/SSL for TCP connections
   - Use DTLS for UDP communication
   - Implement end-to-end encryption in your application

2. **Access Control**
   - Implement authentication at the application layer
   - Use the connect link as a shared secret

3. **Future Improvements**
   - Add mesh-level encryption
   - Implement node authentication
   - Add secure key exchange

### Best Practices

```kotlin
// Use HTTPS for web services
val client = OkHttpClient.Builder()
    .socketFactory(node.socketFactory)
    .build()

// For custom protocols, add encryption
val secureSocket = SSLSocketFactory.getDefault().createSocket(
    node.socketFactory.createSocket(destAddr, port),
    destAddr.hostName,
    port,
    true
)
```

---

## Configuration Options

### NodeConfig

```kotlin
data class NodeConfig(
    val maxHops: Byte = 4,              // Maximum hop count
    val originatorInterval: Long = 1000, // Originator message interval (ms)
    val pingTimeout: Long = 5000,       // Ping timeout (ms)
    val maxPacketSize: Int = 1500,      // Maximum packet size
)
```

### ConnectBand

```kotlin
enum class ConnectBand {
    BAND_2GHZ,  // Better range, more compatible
    BAND_5GHZ,  // Higher speed, less interference
    BAND_6GHZ,  // WiFi 6E (Android 13+)
}
```

### HotspotType

```kotlin
enum class HotspotType {
    WIFI_DIRECT,      // WiFi Direct Group (most compatible)
    LOCAL_ONLY,       // Local Only Hotspot (requires STA+AP)
    AUTO,             // Let system decide
}
```

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Connection dialog on every connect | BSSID not stored | Call `storeBssid()` after first connection |
| Cannot create hotspot | STA+AP not supported | Use WiFi Direct instead |
| High latency | Too many hops | Reduce `maxHops` or reorganize topology |
| Packet loss | Interference | Switch to 5GHz band |

### Debug Logging

```kotlin
val node = AndroidVirtualNode(
    appContext = context,
    dataStore = dataStore,
    logger = MNetLoggerStdout() // Logs to console
)
```

---

## References

- [BATMAN Advanced Protocol](https://www.open-mesh.org/doc/batman-adv/)
- [Android WiFi Direct](https://developer.android.com/guide/topics/connectivity/wifip2p)
- [Android Local Only Hotspot](https://developer.android.com/guide/topics/connectivity/localonlyhotspot)
- [WiFi Bootstrap API](https://developer.android.com/guide/topics/connectivity/wifi-bootstrap)