package com.github.nagyesta.filebarj.io.stream;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * IO specific variant of the Supplier interface.
 *
 * @param <E> the type of the element
 */
@FunctionalInterface
public interface IoSupplier<E> {


    /**
     * Gets the element.
     *
     * @return the element
     * @throws IOException when the stream cannot be supplied
     */
    @NotNull E get() throws IOException;
}
