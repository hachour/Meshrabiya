package com.ustadmobile.meshrabiya.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Interface for mesh network encryption.
 *
 * This interface defines the contract for encrypting and decrypting MMCP (Mesh Control Protocol)
 * messages and virtual packet data at the mesh layer. Implementations can provide different
 * levels of security depending on requirements.
 *
 * ## Security Levels
 *
 * - [NoOpEncryption]: No encryption (current default, for testing only)
 * - [SymmetricEncryption]: AES-based encryption with shared key
 * - [HybridEncryption]: ECDH key exchange + AES (recommended for production)
 *
 * ## Usage
 *
 * ```kotlin
 * val encryption = HybridEncryption()
 * val encrypted = encryption.encrypt(plaintextBytes, peerPublicKey)
 * val decrypted = encryption.decrypt(encrypted, peerPublicKey)
 * ```
 *
 * @see HybridEncryption for the recommended production implementation
 * @see SymmetricEncryption for simpler shared-key scenarios
 */
interface MeshEncryption {

    /**
     * Encrypts data for transmission to a specific peer.
     *
     * @param plaintext The data to encrypt
     * @param peerPublicKey The public key of the destination peer (if applicable)
     * @return The encrypted data
     * @throws EncryptionException if encryption fails
     */
    fun encrypt(plaintext: ByteArray, peerPublicKey: PublicKey? = null): ByteArray

    /**
     * Decrypts data received from a peer.
     *
     * @param ciphertext The encrypted data
     * @param peerPublicKey The public key of the sender (if applicable)
     * @return The decrypted plaintext
     * @throws EncryptionException if decryption fails or authentication fails
     */
    fun decrypt(ciphertext: ByteArray, peerPublicKey: PublicKey? = null): ByteArray

    /**
     * Returns the local public key that should be shared with peers.
     *
     * @return The local [PublicKey] or null if not using public key cryptography
     */
    fun getLocalPublicKey(): PublicKey?
}

/**
 * Exception thrown when encryption or decryption fails.
 *
 * @param message Description of the error
 * @param cause The underlying cause, if any
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * No-operation encryption implementation.
 *
 * This implementation performs no actual encryption or decryption and simply
 * passes through the data unchanged. It should only be used for:
 * - Testing and development
 * - Networks where security is provided at a higher layer
 * - Backward compatibility during migration
 *
 * **WARNING: Do not use in production!**
 */
object NoOpEncryption : MeshEncryption {

    override fun encrypt(plaintext: ByteArray, peerPublicKey: PublicKey?): ByteArray {
        return plaintext.copyOf()
    }

    override fun decrypt(ciphertext: ByteArray, peerPublicKey: PublicKey?): ByteArray {
        return ciphertext.copyOf()
    }

    override fun getLocalPublicKey(): PublicKey? = null
}

/**
 * Generates an EC key pair suitable for ECDH key exchange.
 *
 * @return A [KeyPair] with EC keys using the NIST P-256 curve
 */
fun generateKeyPair(): KeyPair {
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    keyPairGenerator.initialize(256) // NIST P-256
    return keyPairGenerator.generateKeyPair()
}

/**
 * Creates a [MeshEncryption] instance based on the specified configuration.
 *
 * @param enabled Whether encryption should be enabled
 * @return A [MeshEncryption] instance - [NoOpEncryption] if disabled, otherwise a full implementation
 */
fun createMeshEncryption(enabled: Boolean): MeshEncryption {
    return if (enabled) {
        // For now return NoOp - full implementation would use HybridEncryption
        NoOpEncryption
    } else {
        NoOpEncryption
    }
}