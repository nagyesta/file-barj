package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.TempFileAwareTest;
import com.github.nagyesta.filebarj.io.stream.index.ArchiveIndexV1;
import com.github.nagyesta.filebarj.io.stream.index.ArchiveIndexV2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import static com.github.nagyesta.filebarj.io.stream.ReadOnlyArchiveIndex.INDEX_VERSION;
import static java.nio.file.StandardOpenOption.READ;

class IndexVersionTest extends TempFileAwareTest {

    private static final int EXPECTED_CHUNKS = 1;
    private static final int EXPECTED_MAX_CHUNK_SIZE = 1048576;
    private static final int EXPECTED_SIZE = 174;
    private static final int EXPECTED_ENTITIES = 4;

    public Stream<Arguments> indexVersionProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(null, IndexVersion.V1))
                .add(Arguments.of("1", IndexVersion.V1))
                .add(Arguments.of("2", IndexVersion.V2))
                .build();
    }

    public Stream<Arguments> indexFileProvider() {
        return Stream.<Arguments>builder()
                .add(Arguments.of("/example/index.properties", ArchiveIndexV1.class))
                .add(Arguments.of("/example/index_v2.properties", ArchiveIndexV2.class))
                .build();
    }

    @ParameterizedTest
    @MethodSource("indexVersionProvider")
    void testForVersionStringShouldReturnExpectedVersionWhenCalledWithValidInput(
            final String input,
            final IndexVersion expected) {
        //given

        //when
        final var actual = IndexVersion.forVersionString(input);

        //then
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @ParameterizedTest
    @MethodSource("indexFileProvider")
    void testCreateIndexShouldParsePropertiesFilesUsingTheTypeRepresentingTheVersionWhenCalled(
            final String input,
            final Class<? extends ReadOnlyArchiveIndex> expectedType) throws URISyntaxException, IOException {
        //given
        final var path = Path.of(getClass().getResource(input).toURI());
        final var properties = new Properties();
        properties.load(Files.newInputStream(path, READ));
        final var underTest = IndexVersion.forVersionString(properties.getProperty(INDEX_VERSION));

        //when
        final var actual = underTest.createIndex(properties);

        //then
        Assertions.assertEquals(expectedType, actual.getClass());
        Assertions.assertEquals(EXPECTED_CHUNKS, actual.getNumberOfChunks());
        Assertions.assertEquals(EXPECTED_MAX_CHUNK_SIZE, actual.getMaxChunkSizeInBytes());
        Assertions.assertEquals(EXPECTED_SIZE, actual.getLastChunkSizeInBytes());
        Assertions.assertEquals(EXPECTED_SIZE, actual.getTotalSize());
        Assertions.assertEquals(EXPECTED_ENTITIES, actual.getTotalEntities());
    }
}
