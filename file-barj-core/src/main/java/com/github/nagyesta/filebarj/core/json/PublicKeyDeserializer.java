package com.github.nagyesta.filebarj.core.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.github.nagyesta.filebarj.core.crypto.EncryptionKeyUtil;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Deserializer for RSA {@link PublicKey} objects.
 */
public class PublicKeyDeserializer extends JsonDeserializer<PublicKey> {
    @Override
    public PublicKey deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
        final String base64 = p.getValueAsString();
        final byte[] encodedKey = Base64.getDecoder().decode(base64);
        return EncryptionKeyUtil.byteArrayToRsaPublicKey(encodedKey);
    }
}
