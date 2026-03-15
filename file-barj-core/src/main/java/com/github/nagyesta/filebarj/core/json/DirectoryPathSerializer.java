package com.github.nagyesta.filebarj.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Serializer making sure, that the path ends with a / to make it even more clear that this is a directory.
 */
public class DirectoryPathSerializer extends JsonSerializer<Path> {

    private static final String URI_NAME_SEPARATOR = "/";

    @Override
    public void serialize(
            final Path value,
            final JsonGenerator gen,
            final SerializerProvider serializers) throws IOException {
        var uri = value.toUri().toString();
        if (!uri.endsWith(URI_NAME_SEPARATOR)) {
            uri = uri + URI_NAME_SEPARATOR;
        }
        gen.writeString(uri);
    }
}
