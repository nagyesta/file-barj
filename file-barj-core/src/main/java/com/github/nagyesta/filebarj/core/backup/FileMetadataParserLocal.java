package com.github.nagyesta.filebarj.core.backup;

import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.Change;
import com.github.nagyesta.filebarj.core.model.enums.FileType;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.UUID;

/**
 * Local file specific implementation of the {@link FileMetadataParser}.
 */
public class FileMetadataParserLocal implements FileMetadataParser {

    @Override
    public FileMetadata parse(@NonNull final File file, @NonNull final BackupJobConfiguration configuration) {
        final var posixFileAttributes = posixPermissionsQuietly(file);
        final var basicAttributes = basicAttributesQuietly(file);

        return FileMetadata.builder()
                .id(UUID.randomUUID())
                .absolutePath(file.toPath().toAbsolutePath())
                .owner(posixFileAttributes.owner().getName())
                .group(posixFileAttributes.group().getName())
                .posixPermissions(PosixFilePermissions.toString(posixFileAttributes.permissions()))
                .lastModifiedUtcEpochSeconds(basicAttributes.lastModifiedTime().toInstant().getEpochSecond())
                .originalSizeBytes(basicAttributes.size())
                .fileType(FileType.findForAttributes(basicAttributes))
                .originalChecksum(calculateChecksum(file, configuration))
                .hidden(checkIsHiddenQuietly(file))
                .status(Change.NEW)
                .build();
    }

    private PosixFileAttributes posixPermissionsQuietly(final File file) {
        try {
            return Files.readAttributes(file.toPath(), PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (final IOException e) {
            throw new FileParseException(e);
        }
    }

    private BasicFileAttributes basicAttributesQuietly(final File file) {
        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (final IOException e) {
            throw new FileParseException(e);
        }
    }

    private boolean checkIsHiddenQuietly(final File file) {
        try {
            return Files.isHidden(file.toPath());
        } catch (final IOException e) {
            throw new FileParseException(e);
        }
    }


    private String calculateChecksum(final File file, final BackupJobConfiguration configuration) {
        try {
            final var messageDigest = Optional.ofNullable(configuration.getChecksumAlgorithm().getAlgorithmName())
                    .map(DigestUtils::new);
            final var attributes = basicAttributesQuietly(file);
            if (messageDigest.isEmpty() || attributes.isOther() || attributes.isDirectory()) {
                return null;
            } else {
                if (attributes.isSymbolicLink()) {
                    return messageDigest.get().digestAsHex(Files.readSymbolicLink(file.toPath()).toAbsolutePath());
                } else {
                    return messageDigest.get().digestAsHex(file);
                }
            }
        } catch (final IOException e) {
            throw new FileParseException(e);
        }
    }
}
