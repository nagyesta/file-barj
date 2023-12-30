package com.github.nagyesta.filebarj.core.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveEntryLocatorTest {

    @Test
    void testFromEntryPathShouldReturnNullWhenCalledWithInvalidPath() {
        //given
        final var entryPath = "invalid-path";

        //when
        final var actual = ArchiveEntryLocator.fromEntryPath(entryPath);

        //then
        assertNull(actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFromEntryPathShouldThrowExceptionWhenCalledWithNullPath() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> ArchiveEntryLocator.fromEntryPath(null));

        //then + exception
    }

    @Test
    void testFromEntryPathShouldReturnParsedValueWhenCalledWithValidPath() {
        //given
        final var uuid = UUID.randomUUID();
        final var version = 1;
        final var entryPath = "/" + version + "/" + uuid;

        //when
        final var actual = ArchiveEntryLocator.fromEntryPath(entryPath);

        //then
        assertNotNull(actual);
        assertEquals(version, actual.getBackupIncrement());
        assertEquals(uuid, actual.getEntryName());
    }
}
