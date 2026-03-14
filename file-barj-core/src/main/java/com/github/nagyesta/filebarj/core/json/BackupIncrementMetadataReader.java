package com.github.nagyesta.filebarj.core.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.model.AppVersion;
import com.github.nagyesta.filebarj.core.model.ArchivedFileMetadata;
import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import com.github.nagyesta.filebarj.core.persistence.BaseFileSetRepository;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import com.github.nagyesta.filebarj.core.persistence.entities.BaseFileSetId;

import java.io.IOException;
import java.util.*;

import static com.github.nagyesta.filebarj.core.model.BackupIncrementManifest.*;

public class BackupIncrementMetadataReader implements AutoCloseable {

    private final DataStore dataStore;
    private final JsonParser jsonParser;
    private final TypeReference<SortedSet<Integer>> versionsSetType = new TypeReference<>() {
    };
    private final TypeReference<List<String>> fileNamesListType = new TypeReference<>() {
    };
    private final TypeReference<SortedMap<Integer, Map<Integer, String>>> encryptionKeyMapType = new TypeReference<>() {
    };

    public BackupIncrementMetadataReader(
            final DataStore dataStore,
            final JsonParser jsonParser,
            final ObjectMapper objectMapper) {
        this.dataStore = dataStore;
        this.jsonParser = jsonParser;
        jsonParser.configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true);
        jsonParser.setCodec(objectMapper);
    }

    public BackupIncrementManifest read() throws IOException {
        final var fileMetadataSetRepository = dataStore.fileMetadataSetRepository();
        final var files = fileMetadataSetRepository.createFileSet();
        final var archivedFileMetadataSetRepository = dataStore.archivedFileMetadataSetRepository();
        final var archiveEntries = archivedFileMetadataSetRepository.createFileSet();
        final var manifestBuilder = BackupIncrementManifest.builder()
                .dataStore(dataStore)
                .files(files)
                .archivedEntries(archiveEntries);
        var nextToken = jsonParser.nextToken(); //start object
        assertMatches(nextToken, JsonToken.START_OBJECT);
        for (nextToken = jsonParser.nextToken(); nextToken != JsonToken.END_OBJECT; nextToken = jsonParser.nextToken()) {
            final var fieldName = jsonParser.currentName();
            switch (fieldName) {
                case APP_VERSION:
                    manifestBuilder.appVersion(new AppVersion(jsonParser.nextTextValue()));
                    break;
                case START_TIME_UTC_EPOCH_SECONDS:
                    manifestBuilder.startTimeUtcEpochSeconds(jsonParser.nextLongValue(-1L));
                    break;
                case FILE_NAME_PREFIX:
                    manifestBuilder.fileNamePrefix(jsonParser.getText());
                    break;
                case JOB_CONFIGURATION:
                    jsonParser.nextToken();
                    final var configuration = jsonParser.readValueAs(BackupJobConfiguration.class);
                    manifestBuilder.configuration(configuration);
                    break;
                case BACKUP_VERSIONS:
                    jsonParser.nextToken();
                    final var versions = jsonParser.<SortedSet<Integer>>readValueAs(versionsSetType);
                    manifestBuilder.versions(versions);
                    break;
                case ENCRYPTION_KEYS:
                    jsonParser.nextToken();
                    if (nextToken == JsonToken.VALUE_NULL) {
                        break;
                    }
                    final var keysByVersion = jsonParser.<SortedMap<Integer, Map<Integer, String>>>readValueAs(encryptionKeyMapType);
                    manifestBuilder.encryptionKeys(keysByVersion);
                    break;
                case BACKUP_TYPE:
                    jsonParser.nextToken();
                    manifestBuilder.backupType(jsonParser.readValueAs(BackupType.class));
                    break;
                case OPERATING_SYSTEM:
                    manifestBuilder.operatingSystem(jsonParser.nextTextValue());
                    break;
                case INDEX_FILE_NAME:
                    manifestBuilder.indexFileName(jsonParser.nextTextValue());
                    break;
                case DATA_FILE_NAMES:
                    jsonParser.nextToken();
                    final var fileNames = jsonParser.<List<String>>readValueAs(fileNamesListType);
                    manifestBuilder.dataFileNames(fileNames);
                    break;
                case FILES:
                    parseMapOf(fileMetadataSetRepository, files, FileMetadata.class);
                    break;
                case ARCHIVE_ENTRIES:
                    parseMapOf(archivedFileMetadataSetRepository, archiveEntries, ArchivedFileMetadata.class);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + fieldName);
            }
        }
        jsonParser.isExpectedStartObjectToken();
        return manifestBuilder.build();
    }

    private <K extends BaseFileSetId<K>, V extends Comparable<V>> void parseMapOf(
            final BaseFileSetRepository<K, V> repository,
            final K id,
            final Class<V> type) throws IOException {
        var token = jsonParser.nextToken(); //object start
        assertMatches(token, JsonToken.START_OBJECT);
        for (token = jsonParser.nextToken(); token == JsonToken.FIELD_NAME; token = jsonParser.nextToken()) {
            Objects.requireNonNull(UUID.fromString(jsonParser.getText()));
            jsonParser.nextToken();
            final var value = jsonParser.readValueAs(type);
            repository.appendTo(id, value);
        } // object end
        assertMatches(token, JsonToken.END_OBJECT);
    }

    @Override
    public void close() throws Exception {
        jsonParser.close();
    }

    private void assertMatches(
            final JsonToken token,
            final JsonToken expected) throws IOException {
        if (!token.equals(expected)) {
            throw new IOException("Expected " + expected + " but found: " + token.name());
        }
    }
}
