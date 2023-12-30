package com.github.nagyesta.filebarj.core.model.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static com.github.nagyesta.filebarj.core.model.enums.FileType.DIRECTORY;
import static org.mockito.Mockito.*;

class FileTypeTest {

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testFindForAttributesShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> FileType.findForAttributes(null));

        //then + exception
    }

    @Test
    void testFindForAttributesShouldThrowExceptionWhenCalledWithInvalidBasicAttribute() {
        //given
        final var attribute = mock(BasicFileAttributes.class);
        when(attribute.isDirectory()).thenReturn(false);
        when(attribute.isRegularFile()).thenReturn(false);
        when(attribute.isSymbolicLink()).thenReturn(false);
        when(attribute.isOther()).thenReturn(false);

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> FileType.findForAttributes(attribute));

        //then + exception
        verify(attribute).isDirectory();
        verify(attribute).isRegularFile();
        verify(attribute).isSymbolicLink();
        verify(attribute).isOther();
    }

    @Test
    void testStreamContentShouldThrowExceptionWhenCalledOnAFileTypeThatHasNoContent() {
        //given

        //when
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
                try (var ignored = DIRECTORY.streamContent(Path.of("none"))) {
                Assertions.fail("Fail if a stream was opened.");
            }
        });

        //then + exception
    }
}
