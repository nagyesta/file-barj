package com.github.nagyesta.filebarj.core.restore.worker;

import com.github.nagyesta.filebarj.core.config.RestoreTargets;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.Callable;

import static com.github.nagyesta.filebarj.core.backup.worker.PosixFileMetadataParser.DEFAULT_OWNER;

/**
 * Posix compliant implementation of {@link FileMetadataSetter}.
 */
@Getter
@Slf4j
public class PosixFileMetadataSetter implements FileMetadataSetter {

    /**
     * The full access permission string.
     */
    public static final String FULL_ACCESS = "rwxrwxrwx";

    private final RestoreTargets restoreTargets;

    /**
     * Creates a new instance for the specified root path.
     *
     * @param restoreTargets the mappings of the root paths where we would like to restore
     */
    public PosixFileMetadataSetter(@NonNull final RestoreTargets restoreTargets) {
        this.restoreTargets = restoreTargets;
    }

    @Override
    public void setMetadata(@NonNull final FileMetadata metadata) {
        setInitialPermissions(metadata);
        setHiddenStatus(metadata);
        setTimestamps(metadata);
        setOwnerAndGroup(metadata);
        setPermissions(metadata);
    }

    @Override
    public void setInitialPermissions(@NonNull final FileMetadata metadata) {
        if (metadata.getFileType() == FileType.SYMBOLIC_LINK) {
            return;
        }
        final var filePath = restoreTargets.mapToRestorePath(metadata.getAbsolutePath());
        final var posixFilePermissions = PosixFilePermissions.fromString(FULL_ACCESS);
        doSetPermissions(filePath, posixFilePermissions);
    }

    @Override
    public void setPermissions(@NonNull final FileMetadata metadata) {
        if (metadata.getFileType() == FileType.SYMBOLIC_LINK) {
            return;
        }
        final var filePath = restoreTargets.mapToRestorePath(metadata.getAbsolutePath());
        final var posixFilePermissions = PosixFilePermissions.fromString(metadata.getPosixPermissions());
        doSetPermissions(filePath, posixFilePermissions);
    }

    @Override
    public void setTimestamps(@NonNull final FileMetadata metadata) {
        if (metadata.getFileType() == FileType.SYMBOLIC_LINK) {
            return;
        }
        final var filePath = restoreTargets.mapToRestorePath(metadata.getAbsolutePath());
        performIoTaskAndHandleException(() -> {
            final var view = Files.getFileAttributeView(filePath, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            view.setTimes(
                    fromEpochSeconds(metadata.getLastModifiedUtcEpochSeconds()),
                    fromEpochSeconds(metadata.getLastAccessedUtcEpochSeconds()),
                    fromEpochSeconds(metadata.getCreatedUtcEpochSeconds()));
            return null;
        });
    }

    @Override
    public void setOwnerAndGroup(@NonNull final FileMetadata metadata) {
        if (metadata.getFileType() == FileType.SYMBOLIC_LINK) {
            return;
        }
        final var filePath = restoreTargets.mapToRestorePath(metadata.getAbsolutePath());
        try {
            if (!metadata.getOwner().equals(DEFAULT_OWNER) || !metadata.getGroup().equals(DEFAULT_OWNER)) {
                final var attributeView = getPosixFileAttributeView(filePath);
                final var lookupService = filePath.getFileSystem().getUserPrincipalLookupService();
                final var owner = lookupService.lookupPrincipalByName(metadata.getOwner());
                final var group = lookupService.lookupPrincipalByGroupName(metadata.getGroup());
                final var attributes = attributeView.readAttributes();
                if (!owner.equals(attributes.owner())) {
                    attributeView.setOwner(owner);
                }
                if (!group.equals(attributes.group())) {
                    attributeView.setGroup(group);
                }
            }
        } catch (final FileSystemException | UnsupportedOperationException e) {
            log.warn("Could not set owner and group for {}", filePath, e);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sets the permissions on the specified file.
     *
     * @param filePath             the path
     * @param posixFilePermissions the permissions
     */
    protected void doSetPermissions(
            @NotNull final Path filePath,
            @NotNull final Set<PosixFilePermission> posixFilePermissions) {
        performIoTaskAndHandleException(() -> {
            final var attributeView = getPosixFileAttributeView(filePath);
            final var currentPermissions = attributeView.readAttributes().permissions();
            final var targetString = PosixFilePermissions.toString(posixFilePermissions);
            final var currentString = PosixFilePermissions.toString(currentPermissions);
            if (!targetString.equals(currentString)) {
                attributeView.setPermissions(posixFilePermissions);
            }
            return null;
        });
    }

    @Override
    public void setHiddenStatus(final @NonNull FileMetadata metadata) {
        //no-op
    }

    /**
     * Performs the specified IO task and ignores any exceptions.
     *
     * @param task the task
     * @param <T>  the return type
     */
    protected <T> void performIoTaskAndHandleException(final Callable<T> task) {
        try {
            task.call();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull
    private PosixFileAttributeView getPosixFileAttributeView(final Path filePath) {
        final var attributeView = Files.getFileAttributeView(filePath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (attributeView == null) {
            throw new UnsupportedOperationException("POSIX is not supported on the current FS/OS");
        }
        return attributeView;
    }

    @NonNull
    private static FileTime fromEpochSeconds(final Long time) {
        return FileTime.from(Instant.ofEpochSecond(time));
    }
}
