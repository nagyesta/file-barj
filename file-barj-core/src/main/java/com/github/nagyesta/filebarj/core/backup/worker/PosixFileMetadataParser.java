package com.github.nagyesta.filebarj.core.backup.worker;

import com.github.nagyesta.filebarj.core.backup.FileParseException;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * POSIX compliant implementation of the {@link FileMetadataParser}.
 */
public class PosixFileMetadataParser implements FileMetadataParser {

    /**
     * Default owner, in case the POSIX permissions are not available.
     */
    public static final String DEFAULT_OWNER = "-";

    @NotNull
    @Override
    public FileMetadata parse(
            @NonNull final File file, @NonNull final BackupJobConfiguration configuration) {
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return FileMetadata.builder()
                    .id(UUID.randomUUID())
                    .absolutePath(file.toPath().toAbsolutePath())
                    .fileType(FileType.MISSING)
                    .status(Change.DELETED)
                    .build();
        }
        final var posixFileAttributes = posixPermissions(file);
        final var basicAttributes = basicAttributes(file);

        return FileMetadata.builder()
                .id(UUID.randomUUID())
                .fileSystemKey(Optional.ofNullable(basicAttributes.fileKey()).map(String::valueOf).orElse(null))
                .absolutePath(file.toPath().toAbsolutePath())
                .owner(posixFileAttributes.owner())
                .group(posixFileAttributes.group())
                .posixPermissions(posixFileAttributes.permissions())
                .lastModifiedUtcEpochSeconds(basicAttributes.lastModifiedTime().toInstant().getEpochSecond())
                .lastAccessedUtcEpochSeconds(basicAttributes.lastAccessTime().toInstant().getEpochSecond())
                .createdUtcEpochSeconds(basicAttributes.creationTime().toInstant().getEpochSecond())
                .originalSizeBytes(basicAttributes.size())
                .fileType(FileType.findForAttributes(basicAttributes))
                .originalHash(calculateHash(file, configuration))
                .hidden(checkIsHidden(file))
                .status(Change.NEW)
                .build();
    }

    /**
     * Parses the permissions in POSIX format.
     *
     * @param file the file
     * @return the permissions
     */
    protected Permissions posixPermissions(final File file) {
        return performIoTaskAndHandleException(
                () -> new Permissions(Files.readAttributes(file.toPath(), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS)));
    }

    /**
     * Performs an IO task and handles exceptions.
     *
     * @param task the task
     * @param <T>  the return type
     * @return the result of the task
     */
    protected <T> T performIoTaskAndHandleException(final Callable<T> task) {
        try {
            return task.call();
        } catch (final Exception e) {
            throw new FileParseException(e);
        }
    }

    private BasicFileAttributes basicAttributes(final File file) {
        return performIoTaskAndHandleException(
                () -> Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
    }

    private boolean checkIsHidden(final File file) {
        return performIoTaskAndHandleException(() -> Files.isHidden(file.toPath()));
    }

    private String calculateHash(final File file, final BackupJobConfiguration configuration) {
        final var type = FileType.findForAttributes(basicAttributes(file));
        return Optional.of(type)
                .filter(FileType::isContentSource)
                .map(t -> doCalculateHash(file, t, configuration.getHashAlgorithm()))
                .orElse(null);
    }

    private String doCalculateHash(final File file, final FileType type, final HashAlgorithm hash) {
        return performIoTaskAndHandleException(() -> {
            try (var stream = type.streamContent(file.toPath());
                 var hashStream = hash.decorate(OutputStream.nullOutputStream())) {
                IOUtils.copy(stream, hashStream);
                hashStream.flush();
                return hashStream.getDigestValue();
            }
        });

    }

    protected record Permissions(String owner, String group, String permissions) {

        Permissions(final boolean canRead, final boolean canWrite, final boolean canExecute) {
            this(DEFAULT_OWNER, DEFAULT_OWNER, asString(canRead, canWrite, canExecute));
        }

        Permissions(final PosixFileAttributes posixFileAttributes) {
            this(posixFileAttributes.owner().getName(),
                    posixFileAttributes.group().getName(),
                    PosixFilePermissions.toString(posixFileAttributes.permissions()));
        }

        private static String asString(final boolean canRead, final boolean canWrite, final boolean canExecute) {
            final var permissions = new HashSet<PosixFilePermission>();
            if (canRead) {
                permissions.add(PosixFilePermission.OWNER_READ);
                permissions.add(PosixFilePermission.GROUP_READ);
                permissions.add(PosixFilePermission.OTHERS_READ);
            }
            if (canWrite) {
                permissions.add(PosixFilePermission.OWNER_WRITE);
                permissions.add(PosixFilePermission.GROUP_WRITE);
                permissions.add(PosixFilePermission.OTHERS_WRITE);
            }
            if (canExecute) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            return PosixFilePermissions.toString(permissions);
        }
    }
}
