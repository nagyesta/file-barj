package com.github.nagyesta.filebarj.io.stream.internal;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Optionally computes a hash of the data written using this stream.
 */
public class OptionalDigestOutputStream extends DigestOutputStream {
    /**
     * Creates a digest output stream, using the specified output stream and message digest
     * algorithm.
     *
     * @param stream    the output stream.
     * @param algorithm the message digest algorithm to use for this stream.
     */
    public OptionalDigestOutputStream(final OutputStream stream, final String algorithm) {
        super(stream, Optional.ofNullable(algorithm)
                .map(DigestUtils::getDigest)
                .orElse(null));
        on(algorithm != null);
    }

    /**
     * Obtains the hex encoded digest value produced by the used algorithm.
     *
     * @return digest or {@code null} if null hash algorithm was used.
     */
    public String getDigestValue() {
        return Optional.ofNullable(getMessageDigest())
                .map(MessageDigest::digest)
                .map(Hex::encodeHexString)
                .orElse(null);
    }
}
