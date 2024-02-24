package com.github.nagyesta.filebarj.io.stream.model;

import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;

/**
 * Common behavior needed for unpacking a BaRJ archive one entry at a time.
 */
public interface BarjCargoArchiveEntry {
    /**
     * Returns the path of the entry.
     *
     * @return the path
     */
    String getPath();

    /**
     * Returns the type of the entry.
     * @return the type
     */
    FileType getFileType();

    /**
     * Streams the file content of a REGULAR_FILE.
     *
     * @param key The decryption key
     * @return the file content
     * @throws IOException              When the content cannot be read
     * @throws IllegalArgumentException When the entry is not a REGULAR_FILE
     */
    @NotNull InputStream getFileContent(@Nullable SecretKey key) throws IOException;

    /**
     * Streams the link target of a SYMBOLIC_LINK.
     *
     * @param key The decryption key
     * @return the link target
     * @throws IOException              When the content cannot be read
     * @throws IllegalArgumentException When the entry is not a SYMBOLIC_LINK
     */
    @NotNull String getLinkTarget(@Nullable SecretKey key) throws IOException;

    /**
     * Streams the metadata content of an entry.
     *
     * @param key The decryption key
     * @return the metadata or null if the original size is 0
     * @throws IOException When the metadata cannot be read
     */
    @Nullable String getMetadata(@Nullable SecretKey key) throws IOException;

    /**
     * Streams the archived content and metadata of an entry without decryption or decompression.
     *
     * @return the raw content and metadata
     * @throws IOException When the content cannot be read
     */
    @NotNull
    InputStream getRawContentAndMetadata() throws IOException;

    /**
     * Skips the content of an entry. Does nothing if called on a directory entry.
     * @throws IOException When the content cannot be read through.
     */
    void skipContent() throws IOException;

    /**
     * Skips the metadata of an entry.
     * @throws IOException When the metadata cannot be read through.
     */
    void skipMetadata() throws IOException;
}
