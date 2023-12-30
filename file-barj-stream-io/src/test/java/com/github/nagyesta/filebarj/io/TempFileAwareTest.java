package com.github.nagyesta.filebarj.io;

import lombok.Getter;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Getter
public abstract class TempFileAwareTest {
    /**
     * The test directory where this test should create temp files.
     */
    private Path testDataRoot;

    /**
     * Sets up a test directory for the test.
     *
     * @throws IOException If the directory cannot be created.
     */
    @BeforeEach
    protected void setUp() throws IOException {
        final var tempDir = System.getProperty("java.io.tmpdir");
        testDataRoot = Path.of(tempDir, "file-barj-io-test-" + UUID.randomUUID());
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
