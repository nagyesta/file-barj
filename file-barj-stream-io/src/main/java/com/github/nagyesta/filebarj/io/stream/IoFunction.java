package com.github.nagyesta.filebarj.io.stream;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * IO specific variant of the Function interface.
 *
 * @param <S> the source type
 * @param <R> the result type
 */
@FunctionalInterface
public interface IoFunction<S, R> {

    /**
     * Does nothing with the output stream.
     */
    IoFunction<OutputStream, OutputStream> IDENTITY_OUTPUT_STREAM = source -> source;

    /**
     * Does nothing with the input stream.
     */
    IoFunction<InputStream, InputStream> IDENTITY_INPUT_STREAM = source -> source;

    /**
     * Decorates the source stream with optional additional logic.
     *
     * @param source the source stream
     * @return the decorated stream
     * @throws IOException when the stream cannot be decorated
     */
    @NotNull R decorate(@NotNull S source) throws IOException;
}
