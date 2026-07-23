package com.github.nagyesta.filebarj.core.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nagyesta.filebarj.core.model.ValidationRules;
import com.github.nagyesta.filebarj.core.persistence.DataStore;
import jakarta.validation.Validation;
import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

class BackupIncrementMetadataReaderAndWriterTest {

    public Stream<Arguments> resourceAndDataStoreProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        "/backups/ubuntu-backup-for-parser-1-sql.manifest.cargo.json",
                        DataStore.newEmbeddedSqlInstance()))
                .add(Arguments.of(
                        "/backups/windows-backup-for-parser-1-sql.manifest.cargo.json",
                        DataStore.newEmbeddedSqlInstance()))
                .add(Arguments.of(
                        "/backups/ubuntu-backup-for-parser-1-inmem.manifest.cargo.json",
                        DataStore.newInMemoryInstance()))
                .add(Arguments.of(
                        "/backups/windows-backup-for-parser-1-inmem.manifest.cargo.json",
                        DataStore.newInMemoryInstance()))
                .build();
    }

    @ParameterizedTest
    @MethodSource("resourceAndDataStoreProvider")
    @DisabledOnOs(WINDOWS)
    void testReadAndThenWriteShouldReproduceTheInitialJsonWhenCalledWithValidFile(
            final String resourceName,
            final DataStore dataStore) throws Exception {
        //given
        try (dataStore) {
            final String jsonContent;
            try (var input = this.getClass().getResourceAsStream(resourceName);
                 var reader = new InputStreamReader(Objects.requireNonNull(input));
                 var buffered = new BufferedReader(reader)) {
                jsonContent = buffered.lines()
                        .collect(Collectors.joining("\n"));
            }
            final var jsonFactory = new JsonFactory();
            final var objectMapper = new ObjectMapper();
            final var outputBuilder = new StringBuilderWriter();
            try (var validatorFactory = Validation.buildDefaultValidatorFactory();
                 var jsonParser = jsonFactory.createParser(jsonContent);
                 var readerUnderTest = new BackupIncrementMetadataReader(dataStore, jsonParser, objectMapper);
                 var jsonGenerator = jsonFactory.createGenerator(outputBuilder);
                 var writerUnderTest = new BackupIncrementMetadataWriter(jsonGenerator, objectMapper)) {

                final var validator = validatorFactory.getValidator();

                //when
                final var manifest = readerUnderTest.read();
                final var violations = validator.validate(manifest, ValidationRules.Persisted.class);
                writerUnderTest.write(manifest);

                //then
                Assertions.assertTrue(violations.isEmpty());
            }

            Assertions.assertEquals(jsonContent, outputBuilder.toString());
        }
    }
}
