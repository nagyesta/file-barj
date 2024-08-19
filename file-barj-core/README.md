![FileBarj](../.github/assets/FileBarJ-logo-512.png)

[![GitHub license](https://img.shields.io/github/license/nagyesta/file-barj?color=informational)](https://raw.githubusercontent.com/nagyesta/file-barj/main/LICENSE)
[![Java version](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)
[![latest-release](https://img.shields.io/github/v/tag/nagyesta/file-barj?color=blue&logo=git&label=releases&sort=semver)](https://github.com/nagyesta/file-barj/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.nagyesta.file-barj/file-barj-job?logo=apache-maven&color=blue)](https://search.maven.org/search?q=com.github.nagyesta.file-barj)
[![JavaCI](https://img.shields.io/github/actions/workflow/status/nagyesta/file-barj/gradle.yml?logo=github&branch=main)](https://github.com/nagyesta/file-barj/actions/workflows/gradle.yml)
[![codecov](https://img.shields.io/codecov/c/github/nagyesta/file-barj?label=Coverage&flag=core&token=62UC72ZRF0)](https://app.codecov.io/gh/nagyesta/file-barj?flags%5B0%5D=core)

File BaRJ (File Backup and Restore Java) is a multi-platform backup utility for files. It is intended to be a highly configurable tool
that can create secure backups of preconfigured files and folders and can be easily scheduled.

## File BaRJ Core

This module gives a higher level API implementing complex backup operations using the File BaRJ Cargo format
at its core.

> [!WARNING]
> File BaRJ is a free tool that is provided "as is", **without warranty of any kind**. It might be the perfect tool you need, or leave you
> with gigabytes of encrypted hot mess instead of your precious data. By using it, you accept the risk of data loss (among others).

## Quick start guide

### Dependencies

#### Maven

```xml

<dependency>
    <groupId>com.github.nagyesta.file-barj</groupId>
    <artifactId>file-barj-core</artifactId>
    <version>RELEASE</version>
</dependency>
```

#### Gradle

```kotlin
implementation("com.github.nagyesta.file-barj:file-barj-core:+")
```

### Writing an archive

```java
//configuring the backup job
final var configuration = BackupJobConfiguration.builder()
        .backupType(BackupType.FULL)
        .fileNamePrefix("test")
        .compression(CompressionAlgorithm.BZIP2)
        .hashAlgorithm(HashAlgorithm.SHA256)
        .duplicateStrategy(DuplicateHandlingStrategy.KEEP_EACH)
        .destinationDirectory(Path.of("/tmp/backup"))
        .sources(Set.of(BackupSource.builder()
                .path(BackupPath.of(Path.of("/source/dir")))
                .build()))
        .chunkSizeMebibyte(1)
        .encryptionKey(null)
        .build();
final var backupParameters = BackupParameters.builder()
        .job(configuration)
        .forceFull(false)
        .build();
final var backupController = new BackupController(backupParameters);

//executing the backup
backupController.execute(1);
```

### Merging increments

```java
final var mergeParameters = MergeParameters.builder()
        .backupDirectory(Path.of("/tmp/backup"))
        .fileNamePrefix("prefix")
        .kek(null) //optional key encryption key
        .rangeStartEpochSeconds(123L) //Backup start epoch seconds for the first file of the range (inclusive)
        .rangeEndEpochSeconds(234L) //Backup start epoch seconds for the last file of the range (inclusive)
        .build();
final var mergeController = new MergeController(mergeParameters);

mergeController.execute(false);
```

### Reading an archive

```java
//configuring the restore job
final var restoreTargets = new RestoreTargets(
        Set.of(new RestoreTarget(BackupPath.of("/source/dir"), Path.of("/tmp/restore/to"))));
final var restoreTask = RestoreTask.builder()
        .restoreTargets(restoreTargets)
        .dryRun(true)
        .threads(1)
        .deleteFilesNotInBackup(false)
        .includedPath(BackupPath.of("/source/dir")) //optional path filter
        .permissionComparisonStrategy(PermissionComparisonStrategy.STRICT) //optional
        .build();
final var restoreParameters = RestoreParameters.builder()
        .backupDirectory(Path.of("/tmp/backup"))
        .fileNamePrefix("test")
        .kek(null)
        .atPointInTime(123456L)
        .build();
final var restoreController = new RestoreController(restoreParameters);

//executing the restore
restoreController.execute(restoreTask);
```

### Inspecting an archive

```java
//configuring the inspection job
final var backupDir = Path.of("/backup/directory");
final var outputFile = Path.of("/backup/directory");
final var inspectParameters = InspectParameters.builder()
        .backupDirectory(backupDir)
        .fileNamePrefix("file-prefix")
        .kek(null)
        .build();
final var controller = new IncrementInspectionController(inspectParameters);

//list the summary of the available increments
controller.inspectIncrements(System.out);

//list the contents of the latest backup increment
controller.inspectContent(Long.MAX_VALUE, outputFile);
```

### Deleting the increments of an archive

```java
//configuring the deletion job
final var backupDir = Path.of("/backup/directory");
final var outputFile = Path.of("/backup/directory");
final var deletionParameters = IncrementDeletionParameters.builder()
        .backupDirectory(backupDir)
        .fileNamePrefix("file-prefix")
        .kek(null)
        .build();
final var controller = new IncrementDeletionController(deletionParameters);

//Delete all backup increments:
// - starting with the one created at 123456
// - until (exclusive) the next full backup
controller.deleteIncrementsUntilNextFullBackupAfter(123456L);
```

## Further reading

Please read more about the BaRJ backup jobs [here](https://github.com/nagyesta/file-barj/wiki/Backup-job-configuration-tips).
