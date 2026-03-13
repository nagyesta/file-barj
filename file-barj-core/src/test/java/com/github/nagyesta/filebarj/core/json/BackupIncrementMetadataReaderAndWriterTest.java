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
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.condition.OS.WINDOWS;

class BackupIncrementMetadataReaderAndWriterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/backups/ubuntu-backup-for-parser-1.manifest.cargo.json",
            "/backups/windows-backup-for-parser-1.manifest.cargo.json"
    })
    @DisabledOnOs(WINDOWS)
    void testReadAndThenWriteShouldReproduceTheInitialJsonWhenCalledWithValidFile(final String resourceName) throws Exception {
        //given
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
             var dataStore = DataStore.newInMemoryInstance();
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
