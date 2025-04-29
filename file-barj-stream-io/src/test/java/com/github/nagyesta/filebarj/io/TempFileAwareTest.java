package com.github.nagyesta.filebarj.io;

import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
        try {
            PathUtils.deleteDirectory(testDataRoot);
        } catch (final FileSystemException ignored) {
            Files.walkFileTree(testDataRoot, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult visitFile(
                        final @NonNull Path file,
                        final @NonNull BasicFileAttributes attrs) {
                    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                        file.toFile().deleteOnExit();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
