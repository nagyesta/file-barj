package com.github.nagyesta.filebarj.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.backup.ArchivalException;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;

import java.io.IOException;

import static com.github.nagyesta.filebarj.core.model.BackupIncrementManifest.*;

public class BackupIncrementMetadataWriter implements AutoCloseable {

    private final JsonGenerator generator;

    public BackupIncrementMetadataWriter(
            final JsonGenerator generator,
            final ObjectMapper objectMapper) {
        this.generator = generator;
        generator.setCodec(objectMapper);
        generator.setPrettyPrinter(new DefaultPrettyPrinter());
    }

    public void write(final BackupIncrementManifest manifest) throws IOException {
        generator.writeStartObject();
        generator.writeArrayFieldStart(BACKUP_VERSIONS);
        for (final var version : manifest.getVersions()) {
            generator.writeNumber(version);
        }
        generator.writeEndArray();
        generator.writeObjectField(ENCRYPTION_KEYS, manifest.getEncryptionKeys());
        generator.writeStringField(APP_VERSION, manifest.getAppVersion().toJsonValue());
        generator.writeNumberField(START_TIME_UTC_EPOCH_SECONDS, manifest.getStartTimeUtcEpochSeconds());
        generator.writeStringField(BackupIncrementManifest.FILE_NAME_PREFIX, manifest.getFileNamePrefix());
        generator.writeStringField(BackupIncrementManifest.BACKUP_TYPE, manifest.getBackupType().name());
        generator.writeObjectField(JOB_CONFIGURATION, manifest.getConfiguration());
        if (manifest.getOperatingSystem() != null) {
            generator.writeStringField(OPERATING_SYSTEM, manifest.getOperatingSystem());
        }
        generator.writeFieldName(FILE_COLLECTION);
        generator.writeStartObject();
        final var dataStore = manifest.getDataStore();
        dataStore.fileMetadataSetRepository().forEachOrdered(
                manifest.getFiles(), dataStore.singleThreadedPool(), fileMetadata -> {
                    try {
                        generator.writeObjectField(fileMetadata.getId().toString(), fileMetadata);
                    } catch (final IOException e) {
                        throw new ArchivalException(e.getMessage(), e);
                    }
                }
        );
        generator.writeEndObject();
        generator.writeFieldName(ARCHIVE_ENTRY_COLLECTION);
        generator.writeStartObject();
        dataStore.archivedFileMetadataSetRepository().forEachOrdered(
                manifest.getArchivedEntries(), dataStore.singleThreadedPool(), archivedFileMetadata -> {
                    try {
                        generator.writeObjectField(archivedFileMetadata.getId().toString(), archivedFileMetadata);
                    } catch (final IOException e) {
                        throw new ArchivalException(e.getMessage(), e);
                    }
                }
        );
        generator.writeEndObject();
        generator.writeStringField(INDEX_FILE_NAME, manifest.getIndexFileName());
        generator.writeArrayFieldStart(DATA_FILE_NAMES);
        for (final var fileName : manifest.getDataFileNames()) {
            generator.writeString(fileName);
        }
        generator.writeEndArray();
        generator.writeEndObject();
        generator.flush();
    }

    @Override
    public void close() throws Exception {
        generator.flush();
        generator.close();
    }
}
