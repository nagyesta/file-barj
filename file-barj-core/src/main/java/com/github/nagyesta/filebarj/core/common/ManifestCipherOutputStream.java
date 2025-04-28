package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.io.stream.internal.DoOnCloseOutputStream;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.PublicKey;

/**
 * Optionally encrypted output stream for manifests.
 */
public class ManifestCipherOutputStream extends DoOnCloseOutputStream {

    private static final int BYTES_IN_INT = 4;
    private final OutputStream destination;
    private final OutputStream crypto;

    /**
     * Creates a new manifest cipher output stream, encrypts the AES key using the provided RSA key
     * and writes it to the destination. If the RSA key is null, the output stream will not be
     * encrypted.
     *
     * @param destination the destination output stream
     * @param key         the optional RSA key, If null, the output stream will not be encrypted.
     * @throws IOException if an I/O error occurs
     */
    public ManifestCipherOutputStream(
            final @NonNull OutputStream destination,
            final @Nullable PublicKey key) throws IOException {
        this.destination = destination;
        if (key != null) {
            final var secretKey = EncryptionUtil.generateAesKey();
            final var ivBytes = EncryptionUtil.generateSecureRandomBytesForGcmIv();
            final var encryptedKey = EncryptionUtil.encryptBytes(key, secretKey.getEncoded());
            destination.write(ByteBuffer.allocate(BYTES_IN_INT).putInt(encryptedKey.length).array());
            destination.write(encryptedKey);
            crypto = EncryptionUtil.newCipherOutputStream(secretKey, ivBytes).decorate(destination);
        } else {
            crypto = new CountingOutputStream(destination);
        }
    }

    @Override
    protected @NotNull OutputStream getOutputStream() {
        return crypto;
    }

    @Override
    protected void doOnClose() {
        IOUtils.closeQuietly(destination);
    }
}
