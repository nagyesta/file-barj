package com.github.nagyesta.filebarj.io.stream.crypto;

import com.github.nagyesta.filebarj.io.stream.IoFunction;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static java.security.spec.MGF1ParameterSpec.SHA256;
import static javax.crypto.spec.PSource.PSpecified.DEFAULT;

/**
 * Utility for basic Key generation and encryption steps.
 */
@UtilityClass
public class EncryptionUtil {
    private static final String RSA_ALG = "RSA/None/OAEPWithSHA256AndMGF1Padding";
    private static final int RSA_KEY_SIZE = 4096;
    private static final String AES = "AES";
    /**
     * AES key size in bytes.
     */
    public static final int KEY_SIZE_BYTES = 256 / 8;
    /**
     * AES GCM IV size in bytes.
     */
    public static final int GCM_IV_BYTES = 96 / 8;
    /**
     * AES GCM tag size in bits.
     */
    public static final int GCM_TAG_LENGTH_BITS = 128;
    /**
     * The name of the AES GCM transformation.
     */
    public static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String RSA = "RSA";
    private static final String SHA_256 = "SHA-256";
    private static final String MGF_1 = "MGF1";
    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Creates a {@link Cipher} instance using the provided parameters.
     *
     * @param secretKey The AES-256 key.
     * @param ivBytes   The random bytes used as GCM IV
     * @param mode      The mode we want to use this cipher for (encrypt/decrypt).
     * @return the cipher
     */
    public static Cipher createCipher(
            @NonNull final SecretKey secretKey, final byte[] ivBytes, final int mode) {
        try {
            final var iv = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes);
            final var cipher = Cipher.getInstance(AES_GCM, BOUNCY_CASTLE_PROVIDER);
            cipher.init(mode, secretKey, iv);
            return cipher;
        } catch (final Exception e) {
            throw new CryptoException("Could not create AES cypher.", e);
        }
    }

    /**
     * Decrypts the given encrypted byte array using the provided private key.
     *
     * @param privateKey the private key used for decryption
     * @param encrypted  the byte array to be decrypted
     * @return the decrypted byte array
     */
    public byte[] decryptBytes(@NonNull final PrivateKey privateKey, final byte[] encrypted) {
        try {
            final var cipher = Cipher.getInstance(RSA_ALG, BOUNCY_CASTLE_PROVIDER);
            final var oaepParam = new OAEPParameterSpec(SHA_256, MGF_1, SHA256, DEFAULT);
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
            final var cipher = Cipher.getInstance(RSA_ALG, BOUNCY_CASTLE_PROVIDER);
            final var oaepParam = new OAEPParameterSpec(SHA_256, MGF_1, SHA256, DEFAULT);
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
        final var secureRandomKeyBytes = generateSecureRandomBytes();
        return byteArrayToAesKey(secureRandomKeyBytes);
    }

    /**
     * Generates random bytes with a secure random generator.
     *
     * @return the random bytes
     */
    public static byte[] generateSecureRandomBytes() {
        final var secureRandomKeyBytes = new byte[KEY_SIZE_BYTES];
        final var secureRandom = SECURE_RANDOM;
        secureRandom.nextBytes(secureRandomKeyBytes);
        return secureRandomKeyBytes;
    }

    /**
     * Verifies that the key is AES-256.
     *
     * @param key the key
     */
    public static void verifyKeyIsAes256(@NonNull final SecretKey key) {
        if (key.getEncoded().length < KEY_SIZE_BYTES) {
            throw new CryptoException("Key must be AES-256.");
        }
        if (!AES.equals(key.getAlgorithm())) {
            throw new CryptoException("Key must be AES-256.");
        }
    }

    /**
     * Generates random bytes with a secure random generator
     * to be used as GCM IV.
     *
     * @return the random bytes
     */
    public static byte[] generateSecureRandomBytesForGcmIv() {
        final var secureRandomKeyBytes = new byte[GCM_IV_BYTES];
        SECURE_RANDOM.nextBytes(secureRandomKeyBytes);
        return secureRandomKeyBytes;
    }

    /**
     * Generates a random key pair using the RSA algorithm.
     *
     * @return the generated key pair
     */
    public static KeyPair generateRsaKeyPair() {
        try {
            final var generator = KeyPairGenerator.getInstance(RSA);
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
            final var keyFactory = KeyFactory.getInstance(RSA);
            final EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            return keyFactory.generatePublic(publicKeySpec);
        } catch (final Exception e) {
            throw new CryptoException("Unable to deserialize RSA key", e);
        }
    }

    /**
     * Defines how an {@link OutputStream} can be wrapped into a {@link CipherOutputStream} using
     * the provided SecretKey with the AES GCM algorithm.
     *
     * @param encryptionKey The AES secret key
     * @return The function that can encrypt the contents of an {@link OutputStream}
     */
    public static IoFunction<OutputStream, OutputStream> newCipherOutputStream(
            final SecretKey encryptionKey) {
        return newCipherOutputStream(encryptionKey, generateSecureRandomBytesForGcmIv());
    }

    /**
     * Defines how an {@link OutputStream} can be wrapped into a {@link CipherOutputStream} using
     * the provided SecretKey with the AES GCM algorithm.
     *
     * @param encryptionKey The AES secret key
     * @param ivBytes       The random bytes used as GCM IV
     * @return The function that can encrypt the contents of an {@link OutputStream}
     */
    public static IoFunction<OutputStream, OutputStream> newCipherOutputStream(
            final SecretKey encryptionKey, final byte[] ivBytes) {
        if (encryptionKey == null) {
            return IoFunction.IDENTITY_OUTPUT_STREAM;
        }
        verifyKeyIsAes256(encryptionKey);
        final var cipher = createCipher(encryptionKey, ivBytes, Cipher.ENCRYPT_MODE);
        return stream -> {
            writeIv(stream, ivBytes);
            return new CipherOutputStream(stream, cipher);
        };
    }

    /**
     * Defines how an {@link InputStream} can be wrapped into a {@link CipherInputStream} using
     * the provided SecretKey with the AES GCM algorithm.
     *
     * @param decryptionKey The AES secret key
     * @return The function that can decrypt the contents of an {@link InputStream}
     */
    public static IoFunction<InputStream, InputStream> newCipherInputStream(
            final SecretKey decryptionKey) {
        if (decryptionKey == null) {
            return IoFunction.IDENTITY_INPUT_STREAM;
        }
        verifyKeyIsAes256(decryptionKey);
        return stream -> {
            final var ivBytes = readIv(stream);
            final var cipher = createCipher(decryptionKey, ivBytes, Cipher.DECRYPT_MODE);
            return new CipherInputStream(stream, cipher);
        };
    }

    private static void writeIv(final OutputStream stream, final byte[] randomBytes) {
        try {
            stream.write(randomBytes);
        } catch (final IOException e) {
            throw new CryptoException("Failed to write IV value on stream.", e);
        }
    }

    private static byte[] readIv(final InputStream stream) {
        try {
            return stream.readNBytes(GCM_IV_BYTES);
        } catch (final IOException e) {
            throw new CryptoException("Failed to read IV value from stream.", e);
        }
    }
}
