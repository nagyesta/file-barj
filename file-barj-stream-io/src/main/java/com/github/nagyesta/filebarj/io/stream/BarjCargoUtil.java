package com.github.nagyesta.filebarj.io.stream;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utility class for BaRJ cargo streaming.
 */
@UtilityClass
public final class BarjCargoUtil {

    /**
     * The file extension for cargo archives.
     */
    public static final String CARGO = ".cargo";
    /**
     * The file name suffix for index files.
     */
    public static final String INDEX = ".index";
    /**
     * A colon character used for separating the key and value in properties.
     */
    public static final String COLON = ":";
    /**
     * Line break character.
     */
    public static final String LINE_BREAK = "\n";

    /**
     * Calculates the chunk file name based on the given parameters.
     *
     * @param prefix  The prefix
     * @param counter The value of the current counter
     * @return The chunk file name
     */
    public static String toChunkFileName(
            @NonNull final String prefix, final int counter) {
        if (counter <= 0) {
            throw new IllegalArgumentException("Invalid counter: " + counter);
        }
        return String.format("%s.%05d%s", prefix, counter, CARGO);
    }

    /**
     * Calculates the index file name based on the given parameters.
     *
     * @param prefix The prefix
     * @return The chunk file name
     */
    public static String toIndexFileName(
            @NonNull final String prefix) {
        return String.format("%s%s%s", prefix, INDEX, CARGO);
    }

    /**
     * Calculates the index property name prefix for the entry based on its index.
     *
     * @param entryIndex The entry index
     * @return ihe index property name prefix
     */
    public static String entryIndexPrefix(final long entryIndex) {
        if (entryIndex <= 0) {
            throw new IllegalArgumentException("Invalid entry index: " + entryIndex);
        }
        return String.format("%08d", entryIndex);
    }
}
