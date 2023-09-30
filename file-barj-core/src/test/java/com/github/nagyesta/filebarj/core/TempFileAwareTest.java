package com.github.nagyesta.filebarj.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public abstract class TempFileAwareTest {
    /**
     * The object mapper which we can use for JSON conversion.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    protected final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * The test directory where this test should create temp files.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    protected Path testDataRoot;

    /**
     * Sets up a test directory for the test.
     *
     * @throws IOException If the directory cannot be created.
     */
    @BeforeEach
    protected void setUp() throws IOException {
        final var tempDir = System.getProperty("java.io.tmpdir");
        testDataRoot = Path.of(tempDir, "file-barj-test-" + UUID.randomUUID());
        Files.createDirectory(testDataRoot);
    }

    /**
     * Removes the previously created  test directory after the test.
     *
     * @throws IOException If the directory cannot be deleted.
     */
    @AfterEach
    protected void tearDown() throws IOException {
        PathUtils.deleteDirectory(testDataRoot);
    }
}
