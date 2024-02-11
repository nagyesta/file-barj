package com.github.nagyesta.filebarj.job;

import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

public abstract class TempFileAwareTest {

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
        try {
            PathUtils.deleteDirectory(testDataRoot);
        } catch (final FileSystemException ignored) {
            Files.walkFileTree(testDataRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                        file.toFile().deleteOnExit();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
