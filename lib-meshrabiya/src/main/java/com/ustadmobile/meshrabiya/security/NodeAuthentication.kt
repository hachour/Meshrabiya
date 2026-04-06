package com.ustadmobile.meshrabiya.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PublicKey
import kotlin.random.Random

/**
 * Node authentication for mesh networks.
 *
 * This class provides mechanisms to authenticate nodes joining the mesh,
 * preventing unauthorized or rogue nodes from participating in routing.
 *
 * ## Authentication Methods
 *
 * 1. **Shared Secret (Connect Link)**: The connect link URI contains the
 *    hotspot credentials which act as a shared secret. This provides
 *    basic authentication at the WiFi layer.
 *
 * 2. **Public Key Hash Verification**: Nodes can exchange public key
 *    fingerprints and verify them out-of-band (e.g., QR code comparison).
 *
 * 3. **Trust On First Use (TOFU)**: Accept the first public key seen
 *    for a node and reject subsequent connections with different keys.
 *
 * ## Usage
 *
 * ```kotlin
 * // Generate a fingerprint for manual verification
 * val fingerprint = NodeAuthentication.fingerprint(publicKey)
 * // Compare this fingerprint out-of-band
 *
 * // Verify a connecting node
 * val auth = NodeAuthentication()
 * auth.registerNode(nodeVirtualAddr, peerPublicKey)
 * ```
 */
class NodeAuthentication {

    private val knownNodes = mutableMapOf<Int, PublicKey>()
    private val blockedNodes = mutableSetOf<Int>()

    /**
     * Generates a human-readable fingerprint of a public key.
     *
     * The fingerprint is a SHA-256 hash formatted as colon-separated
     * hex bytes, similar to SSH fingerprints.
     *
     * @param publicKey The public key to fingerprint
     * @return A string fingerprint like "A1:B2:C3:..."
     */
    fun fingerprint(publicKey: PublicKey): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(publicKey.encoded)
        return hash.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Generates a short fingerprint suitable for QR code display.
     *
     * @param publicKey The public key to fingerprint
     * @return A short hex string (8 characters)
     */
    fun shortFingerprint(publicKey: PublicKey): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(publicKey.encoded)
        return ByteBuffer.wrap(hash).long.toUInt().toString(16).padStart(8, '0')
    }

    /**
     * Registers a node's public key for authentication.
     *
     * Uses Trust On First Use (TOFU) - the first public key seen for a
     * node is accepted and stored. Subsequent connections with different
     * keys will be rejected.
     *
     * @param virtualAddr The virtual IP address of the node (as Int)
     * @param publicKey The node's public key
     * @return true if the key was accepted, false if it conflicts with a stored key
     */
    fun registerNode(virtualAddr: Int, publicKey: PublicKey): Boolean {
        if (virtualAddr in blockedNodes) {
            return false
        }

        val existingKey = knownNodes[virtualAddr]
        return if (existingKey == null) {
            // First time seeing this node - Trust On First Use
            knownNodes[virtualAddr] = publicKey
            true
        } else {
            // Verify the key matches what we expect
            existingKey.encoded.contentEquals(publicKey.encoded)
        }
    }

    /**
     * Checks if a node is authenticated.
     *
     * @param virtualAddr The virtual IP address of the node (as Int)
     * @return true if the node has been registered and authenticated
     */
    fun isAuthenticated(virtualAddr: Int): Boolean {
        return virtualAddr in knownNodes && virtualAddr !in blockedNodes
    }

    /**
     * Blocks a node from authentication.
     *
     * @param virtualAddr The virtual IP address to block
     */
    fun blockNode(virtualAddr: Int) {
        blockedNodes.add(virtualAddr)
        knownNodes.remove(virtualAddr)
    }

    /**
     * Unblocks a previously blocked node.
     *
     * @param virtualAddr The virtual IP address to unblock
     */
    fun unblockNode(virtualAddr: Int) {
        blockedNodes.remove(virtualAddr)
    }

    /**
     * Removes all registered nodes.
     */
    fun clear() {
        knownNodes.clear()
        blockedNodes.clear()
    }

    /**
     * Generates a random shared secret suitable for symmetric encryption.
     *
     * @param lengthBytes The length of the secret in bytes (default 32 for AES-256)
     * @return A random byte array
     */
    fun generateSharedSecret(lengthBytes: Int = 32): ByteArray {
        return Random.Default.nextBytes(lengthBytes)
    }

    /**
     * Returns the number of registered nodes.
     */
    fun registeredNodeCount(): Int = knownNodes.size

    companion object {
        /**
         * Formats a fingerprint from a SHA-256 hash.
         */
        fun formatFingerprint(hash: ByteArray): String {
            return hash.joinToString(":") { "%02X".format(it) }
        }

        /**
         * Computes the fingerprint of a public key (static utility).
         *
         * @param publicKey The public key
         * @return Colon-separated hex fingerprint
         */
        fun fingerprint(publicKey: PublicKey): String {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(publicKey.encoded)
            return formatFingerprint(hash)
        }
    }
}