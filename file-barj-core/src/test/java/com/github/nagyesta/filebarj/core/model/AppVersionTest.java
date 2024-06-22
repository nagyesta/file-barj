package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppVersionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @SuppressWarnings("resource")
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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

    @Test
    void testValidationShouldPassWhenCalledWithValidAppVersion() throws JsonProcessingException {
        //given

        //when
        final var actual = validator.validate(new AppVersion(0, 1, 2));

        //then
        Assertions.assertEquals(0, actual.size());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testValidationShouldFailWhenCalledWithInvalidMajorVersion() throws JsonProcessingException {
        //given

        //when
        final var actual = validator.validate(new AppVersion(-1, 1, 2));

        //then
        Assertions.assertEquals(1, actual.size());
        final var violation = actual.stream().findFirst().get();
        Assertions.assertEquals("major", violation.getPropertyPath().toString());
        Assertions.assertEquals(-1, violation.getInvalidValue());
        Assertions.assertEquals("must be greater than or equal to 0", violation.getMessage());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testValidationShouldFailWhenCalledWithInvalidMinorVersion() throws JsonProcessingException {
        //given

        //when
        final var actual = validator.validate(new AppVersion(0, -1, 2));

        //then
        Assertions.assertEquals(1, actual.size());
        final var violation = actual.stream().findFirst().get();
        Assertions.assertEquals("minor", violation.getPropertyPath().toString());
        Assertions.assertEquals(-1, violation.getInvalidValue());
        Assertions.assertEquals("must be greater than or equal to 0", violation.getMessage());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testValidationShouldFailWhenCalledWithInvalidPatchVersion() throws JsonProcessingException {
        //given

        //when
        final var actual = validator.validate(new AppVersion(0, 0, -1));

        //then
        Assertions.assertEquals(1, actual.size());
        final var violation = actual.stream().findFirst().get();
        Assertions.assertEquals("patch", violation.getPropertyPath().toString());
        Assertions.assertEquals(-1, violation.getInvalidValue());
        Assertions.assertEquals("must be greater than or equal to 0", violation.getMessage());
    }
}
