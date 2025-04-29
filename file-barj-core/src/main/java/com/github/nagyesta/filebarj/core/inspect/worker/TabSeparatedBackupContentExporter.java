package com.github.nagyesta.filebarj.core.inspect.worker;

import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.NonNull;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * Exports backup content in tab separated format.
 */
public class TabSeparatedBackupContentExporter {

    /**
     * Writes the content of the manifest to the specified output file.
     *
     * @param manifest   The manifest.
     * @param outputFile The output file.
     */
    public void writeManifestContent(
            final @NonNull BackupIncrementManifest manifest,
            final @NonNull Path outputFile) {
        try (var stream = new FileOutputStream(outputFile.toFile());
             var buffered = new BufferedOutputStream(stream);
             var writer = new OutputStreamWriter(buffered, StandardCharsets.UTF_8)) {
            writeTsv(writer, manifest);
        } catch (final IOException e) {
            throw new ArchivalException("Failed to write inspection results.", e);
        }
    }

    private void writeTsv(
            final OutputStreamWriter writer,
            final BackupIncrementManifest manifest) throws IOException {
        writer.write(generateHeader(manifest));
        final var files = manifest.getFiles().values().stream()
                .sorted(Comparator.comparing(FileMetadata::getAbsolutePath))
                .toList();
        for (final var fileMetadata : files) {
            writer.write(toTsv(fileMetadata));
        }
    }

    private String generateHeader(final BackupIncrementManifest manifest) {
        return String.format("permissions\towner\tgroup\tsize\tlast_modified\thash_%s\tpath%n",
                manifest.getConfiguration().getHashAlgorithm().name().toLowerCase());
    }

    private String toTsv(final FileMetadata fileMetadata) {
        return String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s%n",
                fileMetadata.getPosixPermissions(),
                fileMetadata.getOwner(),
                fileMetadata.getGroup(),
                fileMetadata.getOriginalSizeBytes(),
                Instant.ofEpochSecond(fileMetadata.getLastModifiedUtcEpochSeconds()),
                Optional.ofNullable(fileMetadata.getOriginalHash()).orElse("-"),
                fileMetadata.getAbsolutePath());
    }
}
