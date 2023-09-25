package com.github.nagyesta.filebarj.core.crypto;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static java.security.spec.MGF1ParameterSpec.SHA256;
import static javax.crypto.spec.PSource.PSpecified.DEFAULT;

/**
 * Utility for basic Key generation and encryption steps.
 */
@UtilityClass
public class EncryptionKeyUtil {
    private static final String RSA_ALG = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING";
    private static final int RSA_KEY_SIZE = 4096;
    private static final String AES = "AES";
    private static final int KEY_SIZE_BYTES = 256 / 8;
    private static final String RSA = "RSA";
    private static final String SHA_256 = "SHA-256";
    private static final String MGF_1 = "MGF1";

    /**
     * Decrypts the given encrypted byte array using the provided private key.
     *
     * @param privateKey the private key used for decryption
     * @param encrypted  the byte array to be decrypted
     * @return the decrypted byte array
     */
    public byte[] decryptBytes(@NonNull final PrivateKey privateKey, final byte[] encrypted) {
        try {
            final Cipher cipher = Cipher.getInstance(RSA_ALG);
            final OAEPParameterSpec oaepParam = new OAEPParameterSpec(SHA_256, MGF_1, SHA256, DEFAULT);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParam);
            return cipher.doFinal(encrypted);
        } catch (final Exception e) {
            throw new CryptoException("Failed to decrypt encrypted bytes.", e);
        }
    }

    /**
     * Encrypts the given byte array using the provided public key.
     *
     * @param publicKey the public key used for encryption
     * @param bytes     the byte array to be encrypted
     * @return the encrypted byte array
     */
    public byte[] encryptBytes(@NonNull final PublicKey publicKey, final byte[] bytes) {
        try {
            final Cipher cipher = Cipher.getInstance(RSA_ALG);
            final OAEPParameterSpec oaepParam = new OAEPParameterSpec(SHA_256, MGF_1, SHA256, DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParam);
            return cipher.doFinal(bytes);
        } catch (final Exception e) {
            throw new CryptoException("Failed to encrypt bytes.", e);
        }
    }

    /**
     * Generates a random key using the AES algorithm.
     *
     * @return the generated key
     */
    public static SecretKey generateAesKey() {
        final byte[] secureRandomKeyBytes = generateSecureRandomBytes();
        return byteArrayToAesKey(secureRandomKeyBytes);
    }

    /**
     * Generates random bytes with a secure random generator..
     *
     * @return the random bytes
     */
    public static byte[] generateSecureRandomBytes() {
        final byte[] secureRandomKeyBytes = new byte[KEY_SIZE_BYTES];
        final SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(secureRandomKeyBytes);
        return secureRandomKeyBytes;
    }

    /**
     * Generates a random key pair using the RSA algorithm.
     *
     * @return the generated key pair
     */
    public static KeyPair generateRsaKeyPair() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (final NoSuchAlgorithmException e) {
            throw new CryptoException("Unable to generate RSA key pair.", e);
        }
    }

    /**
     * Converts a byte array to an AES key.
     *
     * @param bytes the byte array to convert
     * @return the AES key
     */
    public static SecretKey byteArrayToAesKey(final byte[] bytes) {
        return new SecretKeySpec(bytes, AES);
    }

    /**
     * Converts a byte array to an RSA public key.
     *
     * @param bytes the byte array to convert
     * @return the RSA public key
     */
    public static PublicKey byteArrayToRsaPublicKey(final byte[] bytes) {
        try {
            final KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            final EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            return keyFactory.generatePublic(publicKeySpec);
        } catch (final Exception e) {
            throw new CryptoException("Unable to deserialize RSA key", e);
        }
    }
}
