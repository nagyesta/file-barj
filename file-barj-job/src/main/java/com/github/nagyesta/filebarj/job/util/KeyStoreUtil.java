package com.github.nagyesta.filebarj.job.util;


import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

/**
 * Utility class for key store operations.
 */
@UtilityClass
public final class KeyStoreUtil {

    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NUMBER_OF_BITS_SERIAL = 160;
    private static final long ONE_HUNDRED_YEARS_IN_MILLIS = 3153600000000L;
    private static final String PKCS_12 = "PKCS12";

    /**
     * Opens a PKCS12 key store and returns the private key.
     *
     * @param source    the path of the key store
     * @param alias     the alias of the key
     * @param storePass the password protecting the key store
     * @param keyPass   the password protecting the key
     * @return the private key
     */
    public static PrivateKey readPrivateKey(
            final @NotNull Path source,
            final @NotNull String alias,
            final char @NotNull [] storePass,
            final char @NotNull [] keyPass) {
        try {
            return (PrivateKey) KeyStore.getInstance(source.toFile(), storePass)
                    .getKey(alias, keyPass);
        } catch (final Exception e) {
            throw new ArchivalException("Failed to read private key from key store: " + source, e);
        }
    }

    /**
     * Opens a PKCS12 key store and returns the public key.
     *
     * @param source    the path of the key store
     * @param alias     the alias of the key
     * @param storePass the password protecting the key store
     * @return the public key
     */
    public static PublicKey readPublicKey(
            final @NotNull Path source,
            final @NotNull String alias,
            final char @NotNull [] storePass) {
        try {
            return KeyStore.getInstance(source.toFile(), storePass)
                    .getCertificateChain(alias)[0].getPublicKey();
        } catch (final Exception e) {
            throw new ArchivalException("Failed to read public key from key store: " + source, e);
        }
    }

    /**
     * Creates and writes a PKCS12 key store and stores the key pair in it.
     *
     * @param target    the path of the key store
     * @param alias     the alias of the key
     * @param keyPair   the key pair
     * @param storePass the password protecting the key store
     * @param keyPass   the password protecting the key
     */
    public static void writeKey(
            final @NotNull Path target,
            final @NotNull String alias,
            final @NotNull KeyPair keyPair,
            final char @NotNull [] storePass,
            final char @NotNull [] keyPass) {
        try (var stream = Files.newOutputStream(target)) {
            final var keyStore = newKeyStore(storePass);
            final var chain = createChainFor(keyPair);
            keyStore.setKeyEntry(alias, keyPair.getPrivate(), keyPass, chain);
            keyStore.store(stream, storePass);
        } catch (final Exception e) {
            throw new ArchivalException("Failed to write key to key store: " + target, e);
        }
    }

    private static @NotNull Certificate[] createChainFor(
            final @NotNull KeyPair keyPair) throws OperatorCreationException, CertificateException {
        final var subject = new X500Name("CN=Ignore");
        final var now = new Date(Instant.now().toEpochMilli());
        final var future = new Date(Instant.now().toEpochMilli() + ONE_HUNDRED_YEARS_IN_MILLIS);
        final var serial = new BigInteger(NUMBER_OF_BITS_SERIAL, RANDOM);
        final var signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BOUNCY_CASTLE_PROVIDER)
                .build(keyPair.getPrivate());
        final var publicKey = keyPair.getPublic();
        final var holder = new JcaX509v3CertificateBuilder(subject, serial, now, future, subject, publicKey)
                .build(signer);
        final var converter = new JcaX509CertificateConverter();
        converter.setProvider(BOUNCY_CASTLE_PROVIDER);
        return new X509Certificate[]{converter.getCertificate(holder)};
    }

    private static @NotNull KeyStore newKeyStore(
            final char @NotNull [] password)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        final var store = KeyStore.getInstance(PKCS_12);
        store.load(null, password);
        return store;
    }
}
