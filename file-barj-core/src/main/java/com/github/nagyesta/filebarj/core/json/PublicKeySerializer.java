package com.github.nagyesta.filebarj.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;

/**
 * Serializer for {@link PublicKey} objects.
 */
public class PublicKeySerializer extends JsonSerializer<PublicKey> {
    @Override
    public void serialize(final PublicKey value, final JsonGenerator gen, final SerializerProvider serializers) throws IOException {
        gen.writeString(Base64.getEncoder().encodeToString(value.getEncoded()));
    }
}
