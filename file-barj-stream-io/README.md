![FileBarj](../.github/assets/FileBarJ-logo-512.png)

[![GitHub license](https://img.shields.io/github/license/nagyesta/file-barj?color=informational)](https://raw.githubusercontent.com/nagyesta/file-barj/main/LICENSE)
[![Java version](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)
[![latest-release](https://img.shields.io/github/v/tag/nagyesta/file-barj?color=blue&logo=git&label=releases&sort=semver)](https://github.com/nagyesta/file-barj/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.nagyesta.file-barj/file-barj-job?logo=apache-maven&color=blue)](https://search.maven.org/search?q=com.github.nagyesta.file-barj)

[![JavaCI](https://img.shields.io/github/actions/workflow/status/nagyesta/file-barj/gradle.yml?logo=github&branch=main)](https://github.com/nagyesta/file-barj/actions/workflows/gradle.yml)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=nagyesta_file-barj&metric=coverage)](https://sonarcloud.io/summary/new_code?id=nagyesta_file-barj)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=nagyesta_file-barj&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=nagyesta_file-barj)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=nagyesta_file-barj&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=nagyesta_file-barj)

File BaRJ (File Backup and Restore Java) is a multi-platform backup utility for files. It is intended to be a highly configurable tool
that can create secure backups of preconfigured files and folders and can be easily scheduled.

## File BaRJ Stream I/O

This module defines the BaRJ Cargo archives and provides a low level SDK for them.

> [!WARNING]
> File BaRJ is a free tool that is provided "as is", **without warranty of any kind**. It might be the perfect tool you need, or leave you
> with gigabytes of encrypted hot mess instead of your precious data. By using it, you accept the risk of data loss (among others).

## Quick start guide

### Dependencies

#### Maven

```xml

<dependency>
    <groupId>com.github.nagyesta.file-barj</groupId>
    <artifactId>file-barj-stream-io</artifactId>
    <version>RELEASE</version>
</dependency>
```

#### Gradle

```kotlin
implementation("com.github.nagyesta.file-barj:file-barj-stream-io:+")
```

### Writing an archive

```java
final var config = BarjCargoOutputStreamConfiguration.builder()
        .hashAlgorithm("SHA-256")
        .prefix("barj")
        .maxFileSizeMebibyte(1) //our minimal data won't reach the chunk limit
        .compressionFunction(IoFunction.IDENTITY_OUTPUT_STREAM) //no compression to see the content
        .folder(Path.of("/tmp/dir"))
        .build();
try (var stream = new BarjCargoArchiverFileOutputStream(config)) {
    stream.addDirectoryEntity("/dir", null,
            "arbitrary metadata of /dir");
    stream.addFileEntity("/dir/file1.ext", new ByteArrayInputStream("file1 content".getBytes()), null,
            "arbitrary metadata of /dir/file1.ext");
    stream.addSymbolicLinkEntity("/dir/file2.ext", "/dir/file1.txt", null,
            "arbitrary metadata of /dir/file2.ext");
    stream.addFileEntity("/dir/file3.ext", new ByteArrayInputStream("file3 content".getBytes()), null,
            "arbitrary metadata of /dir/file3.ext");
}
```

### Reading an archive

```java
final var config = BarjCargoInputStreamConfiguration.builder()
        .hashAlgorithm("SHA-256")
        .prefix("barj")
        .compressionFunction(IoFunction.IDENTITY_INPUT_STREAM)
        .folder(Path.of("/tmp/dir"))
        .build();

final var source = new BarjCargoArchiveFileInputStreamSource(config);
final var iterator = source.getIterator();

Assertions.assertTrue(iterator.hasNext());
//verify /dir
final var dir = iterator.next();
Assertions.assertEquals(FileType.DIRECTORY, dir.getFileType());
Assertions.assertEquals("/dir", dir.getPath());
Assertions.assertEquals("arbitrary metadata of /dir", dir.getMetadata(null));
//verify /dir/file1.ext
Assertions.assertTrue(iterator.hasNext());
final var file1 = iterator.next();
Assertions.assertEquals(FileType.REGULAR_FILE, file1.getFileType());
Assertions.assertEquals("/dir/file1.ext", file1.getPath());
//the order is important, read content first!
Assertions.assertArrayEquals("file1 content".getBytes(), file1.getFileContent(null).readAllBytes());
Assertions.assertEquals("arbitrary metadata of /dir/file1.ext", file1.getMetadata(null));
//verify /dir/file2.ext
Assertions.assertTrue(iterator.hasNext());
final var file2 = iterator.next();
Assertions.assertEquals(FileType.SYMBOLIC_LINK, file2.getFileType());
Assertions.assertEquals("/dir/file2.ext", file2.getPath());
Assertions.assertEquals("/dir/file1.txt", file2.getLinkTarget(null));
Assertions.assertEquals("arbitrary metadata of /dir/file2.ext", file2.getMetadata(null));
//verify /dir/file3.ext
Assertions.assertTrue(iterator.hasNext());
final var file3 = iterator.next();
Assertions.assertEquals(FileType.REGULAR_FILE, file3.getFileType());
Assertions.assertEquals("/dir/file3.ext", file3.getPath());
Assertions.assertArrayEquals("file3 content".getBytes(), file3.getFileContent(null).readAllBytes());
Assertions.assertEquals("arbitrary metadata of /dir/file3.ext", file3.getMetadata(null));

Assertions.assertFalse(iterator.hasNext());
```

## Further reading

Please read more about the BaRJ Cargo file format [here](https://github.com/nagyesta/file-barj/wiki/About-the-BaRJ-Cargo-format).
