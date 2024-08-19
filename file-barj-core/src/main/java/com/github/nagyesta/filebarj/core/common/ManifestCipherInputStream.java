package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.io.stream.internal.DoOnCloseInputStream;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.PrivateKey;

/**
 * Optionally encrypted input stream for manifests.
 */
public class ManifestCipherInputStream extends DoOnCloseInputStream {

    private static final int BYTES_IN_INT = 4;
    private final InputStream source;
    private final InputStream crypto;

    /**
     * Creates a new manifest cipher input stream, reads and decrypts the AES key using the provided
     * RSA key. If the RSA key is null, the input stream will not be decrypted.
     *
     * @param source the source input stream
     * @param key    the optional RSA key, If null, the input stream will not be decrypted.
     * @throws IOException if an I/O error occurs
     */
    public ManifestCipherInputStream(
            final @NonNull InputStream source, final @Nullable PrivateKey key) throws IOException {
        this.source = source;
        if (key != null) {
            final var encryptedKeyLength = ByteBuffer.wrap(source.readNBytes(BYTES_IN_INT)).getInt();
            final var encryptedKey = source.readNBytes(encryptedKeyLength);
            final var secretKeyBytes = EncryptionUtil.decryptBytes(key, encryptedKey);
            final var secretKey = EncryptionUtil.byteArrayToAesKey(secretKeyBytes);
            crypto = EncryptionUtil.newCipherInputStream(secretKey).decorate(source);
        } else {
            crypto = BoundedInputStream.builder().setInputStream(source).get();
        }
    }

    @Override
    protected @NotNull InputStream getInputStream() {
        return crypto;
    }

    @Override
    protected void doOnClose() {
        IOUtils.closeQuietly(source);
    }
}
