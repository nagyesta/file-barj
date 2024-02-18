package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.TempFileAwareTest;
import com.github.nagyesta.filebarj.core.model.FileMetadata;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionComparisonStrategyTest extends TempFileAwareTest {

    public Stream<Arguments> strictStrategyDataProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r-----"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        false))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "r--r--r--"),
                        false))
                .add(Arguments.of(createFileMetadata("-", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        false))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        false))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata(null, null, null),
                        false))
                .add(Arguments.of(createFileMetadata(null, null, null),
                        createFileMetadata("owner", "-", "rw-r--r--"),
                        false))
                .build();
    }

    public Stream<Arguments> permissionOnlyStrategyDataProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r-----"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        false))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "r--r--r--"),
                        false))
                .add(Arguments.of(createFileMetadata("-", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata(null, null, null),
                        false))
                .add(Arguments.of(createFileMetadata(null, null, null),
                        createFileMetadata("owner", "-", "rw-r--r--"),
                        false))
                .build();
    }

    public Stream<Arguments> relaxedStrategyDataProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r-----"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "r--r--r--"),
                        false))
                .add(Arguments.of(createFileMetadata("-", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata(null, null, null),
                        false))
                .add(Arguments.of(createFileMetadata(null, null, null),
                        createFileMetadata("owner", "-", "rw-r--r--"),
                        false))
                .build();
    }

    public Stream<Arguments> ignoreStrategyDataProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r-----"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "r--r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("-", "group", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata("owner", "group", "rw-r--r--"),
                        true))
                .add(Arguments.of(createFileMetadata("owner", "-", "rw-r--r--"),
                        createFileMetadata(null, null, null),
                        true))
                .add(Arguments.of(createFileMetadata(null, null, null),
                        createFileMetadata("owner", "-", "rw-r--r--"),
                        true))
                .build();
    }

    @ParameterizedTest
    @MethodSource("strictStrategyDataProvider")
    void testStrictStrategyShouldNotAllowAnyDifferencesWhenCalled(
            final FileMetadata previousMetadata, final FileMetadata currentMetadata, final boolean expectedResult) {
        //given

        //when
        final var result = PermissionComparisonStrategy.STRICT.matches(previousMetadata, currentMetadata);
        //then
        assertEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("permissionOnlyStrategyDataProvider")
    void testPermissionOnlyStrategyShouldAllowDifferencesInOwnerAndGroupNameWhenCalled(
            final FileMetadata previousMetadata, final FileMetadata currentMetadata, final boolean expectedResult) {
        //given

        //when
        final var result = PermissionComparisonStrategy.PERMISSION_ONLY.matches(previousMetadata, currentMetadata);
        //then
        assertEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("relaxedStrategyDataProvider")
    void testRelaxedStrategyShouldAllowDifferencesInOwnerAndGroupNameWhenCalled(
            final FileMetadata previousMetadata, final FileMetadata currentMetadata, final boolean expectedResult) {
        //given

        //when
        final var result = PermissionComparisonStrategy.RELAXED.matches(previousMetadata, currentMetadata);
        //then
        assertEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("ignoreStrategyDataProvider")
    void testIgnoreStrategyShouldAllowDifferencesInOwnerAndGroupNameWhenCalled(
            final FileMetadata previousMetadata, final FileMetadata currentMetadata, final boolean expectedResult) {
        //given

        //when
        final var result = PermissionComparisonStrategy.IGNORE.matches(previousMetadata, currentMetadata);
        //then
        assertEquals(expectedResult, result);
    }

    private FileMetadata createFileMetadata(final String owner, final String group, final String permissions) {
        final var mock = mock(FileMetadata.class);
        when(mock.getOwner()).thenReturn(owner);
        when(mock.getGroup()).thenReturn(group);
        when(mock.getPosixPermissions()).thenReturn(permissions);
        return mock;
    }
}
