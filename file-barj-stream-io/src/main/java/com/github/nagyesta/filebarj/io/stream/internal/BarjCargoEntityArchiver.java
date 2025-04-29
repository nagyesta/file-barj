package com.github.nagyesta.filebarj.io.stream.internal;

import com.github.nagyesta.filebarj.io.stream.BarjCargoBoundarySource;
import com.github.nagyesta.filebarj.io.stream.IoFunction;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import com.github.nagyesta.filebarj.io.stream.enums.EntityArchivalStage;
import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import javax.crypto.SecretKey;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a single entity inside the BaRJ cargo archive. It can provide the appropriate
 * {@link OutputStream} instances for the caller to allow population of the entity.
 * The typical use case is expected to perform the following steps in sequence:
 * <ol>
 *     <li>{@link #openContentStream()}</li>
 *     <li>write content</li>
 *     <li>{@link #closeContentStream()}</li>
 *     <li>{@link #openMetadataStream()}</li>
 *     <li>write metadata</li>
 *     <li>{@link #closeMetadataStream()}</li>
 *     <li>{@link #close()}</li>
 * </ol>
 */
@Slf4j
public class BarjCargoEntityArchiver implements Closeable, BarjCargoBoundarySource {

    @Getter
    private final String path;
    @Getter
    private final FileType fileType;
    private final BaseBarjCargoArchiverFileOutputStream destinationStream;
    private final IoFunction<BaseBarjCargoArchiverFileOutputStream, ArchiveEntryOutputStream> streamFunction;
    @Getter
    private final boolean encrypted;
    private ArchiveEntryOutputStream currentStream;
    @Getter
    private BarjCargoEntryBoundaries contentBoundary;
    @Getter
    private BarjCargoEntryBoundaries metadataBoundary;
    @Getter
    private EntityArchivalStage status;
    private final ReentrantLock statusLock = new ReentrantLock();

    /**
     * Creates a new instance and sets the mandatory fields.
     *
     * @param path              The logical path where the entity should be stored in the archive.
     * @param fileType          The type of the entity
     * @param destinationStream The stream where we should write the contents of this entity
     * @param encryptionKey     The encryption key which should be used for the encryption of the
     *                          entry (optional, encryption will be turned off if null)
     */
    public BarjCargoEntityArchiver(
            final String path,
            final FileType fileType,
            final BaseBarjCargoArchiverFileOutputStream destinationStream,
            final SecretKey encryptionKey) {
        if (destinationStream.hasOpenEntity()) {
            throw new IllegalStateException(
                    "Cannot create another archiver when there is an open entity already.");
        }
        this.path = path;
        this.fileType = fileType;
        this.destinationStream = destinationStream;
        this.streamFunction = stream -> new ArchiveEntryOutputStream(
                stream, EncryptionUtil.newCipherOutputStream(encryptionKey));
        this.encrypted = encryptionKey != null;
        this.status = fileType.getStartStage();
    }

    /**
     * Opens a stream for the content of the entity.
     *
     * @return content stream
     * @throws IOException When IO exception occurs during the open operation
     */
    protected OutputStream openContentStream() throws IOException {
        return openStreamForStage(EntityArchivalStage.CONTENT, EntityArchivalStage.PRE_CONTENT);
    }

    /**
     * Closes the content stream (previously opened by {@link #openContentStream()}.
     *
     * @throws IOException When an IO exception occurs during the close operation
     */
    protected void closeContentStream() throws IOException {
        closeStreamForStage(EntityArchivalStage.CONTENT, () -> this.contentBoundary = currentStream.getEntityBoundary());
    }

    /**
     * Opens a stream for the file metadata of the entity.
     *
     * @return metadata stream
     * @throws IOException When IO exception occurs during the open operation
     */
    protected OutputStream openMetadataStream() throws IOException {
        return openStreamForStage(EntityArchivalStage.METADATA, EntityArchivalStage.PRE_METADATA);
    }

    /**
     * Closes the metadata stream (previously opened by {@link #openMetadataStream()}.
     *
     * @throws IOException When an IO exception occurs during the close operation
     */
    protected void closeMetadataStream() throws IOException {
        closeStreamForStage(EntityArchivalStage.METADATA, () -> this.metadataBoundary = currentStream.getEntityBoundary());
    }

    @Override
    public void close() throws IOException {
        if (this.status == EntityArchivalStage.CLOSED) {
            return;
        }
        statusLock.lock();
        try {
            if (status == EntityArchivalStage.CONTENT) {
                closeContentStream();
                //noinspection resource
                openMetadataStream();
                closeMetadataStream();
            } else if (status == EntityArchivalStage.METADATA) {
                closeMetadataStream();
            }
        } finally {
            statusLock.unlock();
        }
    }

    private OutputStream openStreamForStage(
            final EntityArchivalStage targetStage,
            final EntityArchivalStage expectedStage) throws IOException {
        if (status.getNext() != targetStage) {
            throw new IllegalStateException(
                    "Cannot open stream due to unexpected stream stage: " + status + ". Expected: " + expectedStage);
        }
        statusLock.lock();
        try {
            if (status.getNext() == targetStage) {
                this.currentStream = streamFunction.decorate(destinationStream);
                status = status.getNext();
            }
            return currentStream;
        } finally {
            statusLock.unlock();
        }
    }

    @SuppressWarnings("java:S2589") //the method may be called by multiple threads
    private void closeStreamForStage(
            final EntityArchivalStage expectedStage,
            final Runnable persistBoundary) throws IOException {
        if (status != expectedStage) {
            throw new IllegalStateException(
                    "Cannot close stream due to unexpected stream stage: " + status + ". Expected: " + expectedStage);
        }
        statusLock.lock();
        try {
            if (status == expectedStage) {
                currentStream.flush();
                IOUtils.close(currentStream);
                persistBoundary.run();
                currentStream = null;
                status = status.getNext();
            }
        } finally {
            statusLock.unlock();
        }
    }
}
