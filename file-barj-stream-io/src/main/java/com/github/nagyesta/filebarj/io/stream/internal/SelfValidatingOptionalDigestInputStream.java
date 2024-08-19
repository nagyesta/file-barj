package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.exception.ArchiveIntegrityException;
import lombok.NonNull;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

/**
 * Optionally computes a hash of the data read using this stream.
 */
public class SelfValidatingOptionalDigestInputStream extends DigestInputStream {

    private final String expectedDigest;
    private String calculatedDigestValue;

    /**
     * Creates a digest input stream, using the specified input stream and message digest algorithm.
     *
     * @param stream         the input stream.
     * @param algorithm      the message digest algorithm to use for this stream.
     * @param expectedDigest the expected digest of the data read fully.
     */
    public SelfValidatingOptionalDigestInputStream(
            final @NonNull InputStream stream,
            final @Nullable String algorithm,
            final @Nullable String expectedDigest) {
        super(stream, Optional.ofNullable(algorithm)
                .map(DigestUtils::getDigest)
                .orElse(null));
        this.expectedDigest = expectedDigest;
        on(algorithm != null);
    }

    @Override
    public void close() throws IOException {
        super.close();
        verifyDigest();
    }

    /**
     * Obtains the hex encoded digest value produced by the used algorithm.
     *
     * @return digest or {@code null} if null hash algorithm was used.
     */
    protected String getDigestValue() {
        if (calculatedDigestValue == null) {
            this.calculatedDigestValue = Optional.ofNullable(getMessageDigest())
                    .map(MessageDigest::digest)
                    .map(Hex::encodeHexString)
                    .orElse(null);
        }
        return calculatedDigestValue;
    }

    private void verifyDigest() {
        final var digestValue = getDigestValue();
        if (!Objects.equals(digestValue, expectedDigest)) {
            throw new ArchiveIntegrityException(
                    "Digests do not match. Expected: " + expectedDigest + " Actual: " + digestValue);
        }
    }
}
