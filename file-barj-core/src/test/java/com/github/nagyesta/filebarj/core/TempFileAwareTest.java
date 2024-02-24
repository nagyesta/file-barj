package com.github.nagyesta.filebarj.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
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

    /**
     * Copies the selected backup files to the test directory.
     *
     * @param prefixes   The prefixes of the backup files
     * @param backupPath The path of the backup directory
     * @throws IOException If the files cannot be copied
     */
    @SuppressWarnings("DataFlowIssue")
    protected void prepareBackupFiles(final Set<String> prefixes, final Path backupPath) throws IOException {
        for (final var prefix : prefixes) {
            final var backupFiles = Set.of(prefix + ".00001.cargo", prefix + ".index.cargo", prefix + ".manifest.cargo");
            for (final var filename : backupFiles) {
                final var path = new File(getClass().getResource("/backups/" + filename).getFile()).toPath().toAbsolutePath();
                Files.copy(path, backupPath.resolve(filename));
            }
        }
    }
}
