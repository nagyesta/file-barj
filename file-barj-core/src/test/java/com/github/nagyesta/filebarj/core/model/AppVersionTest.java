package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppVersionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializeShouldRecreatePreviousStateWhenCalledOnSerializedState() throws JsonProcessingException {
        //given
        final var expected = new AppVersion(1, 2, 3);
        final var json = objectMapper.writer().writeValueAsString(expected);

        //when
        final AppVersion actual = objectMapper.readerFor(AppVersion.class).readValue(json);

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertEquals("\"1.2.3\"", json);
    }

    @Test
    void testNoArgConstructorShouldSetDefaultVersionWhenCalled() throws JsonProcessingException {
        //given
        final var expected = objectMapper.readerFor(AppVersion.class)
                .readValue("\"" + AppVersion.DEFAULT_VERSION + "\"");

        //when
        final var actual = new AppVersion();

        //then
        Assertions.assertEquals(expected, actual);
        Assertions.assertNotEquals(AppVersion.DEFAULT_VERSION, "0.0.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1.2.3.4", "1-2-3", "1_2_3"})
    void testConstructorShouldThrowExceptionWhenStringIsInWrongFormat(final String value) {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> new AppVersion(value));

        //then + exception
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.0"})
    void testConstructorShouldThrowExceptionWhenStringHasTooFewTokens(final String value) {
        //given

        //when
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> new AppVersion(value));

        //then + exception
    }
}
