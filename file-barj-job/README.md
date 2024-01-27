![FileBarj](../.github/assets/FileBarJ-logo-512.png)

[![GitHub license](https://img.shields.io/github/license/nagyesta/file-barj?color=informational)](https://raw.githubusercontent.com/nagyesta/file-barj/main/LICENSE)
[![Java version](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)
[![latest-release](https://img.shields.io/github/v/tag/nagyesta/file-barj?color=blue&logo=git&label=releases&sort=semver)](https://github.com/nagyesta/file-barj/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.nagyesta.file-barj/file-barj-job?logo=apache-maven&color=blue)](https://search.maven.org/search?q=com.github.nagyesta.file-barj)
[![JavaCI](https://img.shields.io/github/actions/workflow/status/nagyesta/file-barj/gradle.yml?logo=github&branch=main)](https://github.com/nagyesta/file-barj/actions/workflows/gradle.yml)
[![codecov](https://img.shields.io/codecov/c/github/nagyesta/file-barj?label=Coverage&flag=job&token=62UC72ZRF0)](https://app.codecov.io/gh/nagyesta/file-barj?flags%5B0%5D=job)

File BaRJ (File Backup and Restore Java) is a multi-platform backup utility for files. It is intended to be a highly configurable tool
that can create secure backups of preconfigured files and folders and can be easily scheduled.

## File BaRJ Job

This module provides a CLI entry point for defining and executing File BaRJ backup tasks using the high level API provided by the [File BaRJ Core](../file-barj-core/README.md) package.

> [!WARNING]
> File BaRJ is a free tool that is provided "as is", **without warranty of any kind**. It might be the perfect tool you need, or leave you
> with gigabytes of encrypted hot mess instead of your precious data. By using it, you accept the risk of data loss (among others).

## Quick start guide

### Generating a key pair

Execute the following command (assuming that your executable is named accordingly).

```commandline
java -jar build/libs/file-barj-job.jar \
     --gen-keys \
     --key-store keys.p12 \
     --key-alias alias
```

Follow the I/O prompts on the console to provide the necessary passwords.
Save the printed public key as you will need it later when configuring your backup job.

### Executing a backup

Configure your backup job using the 
[Backup job configuration tips](https://github.com/nagyesta/file-barj/wiki/Backup-job-configuration-tips) page.

Execute the following command (assuming that your executable is named accordingly).

```commandline
java -jar build/libs/file-barj-job.jar \
     --backup \
     --config config.json \
     --threads 2
```

### Restoring a backup to a directory

Execute the following command (assuming that your executable is named accordingly).

```commandline
java -jar build/libs/file-barj-job.jar \
     --restore \
     --backup-source /backup/directory/path \
     --prefix backup-job-file-prefix \
     --target-mapping /original/path=/restore/path \
     --dry-run true \
     --delete-missing true \
     --key-store keys.p12 \
     --key-alias alias \
     --threads 2
```

### Inspecting the available increments of a backup

Execute the following command (assuming that your executable is named accordingly).

```commandline
java -jar build/libs/file-barj-job.jar \
     --inspect-increments \
     --backup-source /backup/directory/path \
     --prefix backup-job-file-prefix \
     --key-store keys.p12 \
     --key-alias alias
```

### Inspecting the content of a backup increment

Execute the following command (assuming that your executable is named accordingly).

```commandline
java -jar build/libs/file-barj-job.jar \
     --inspect-content \
     --backup-source /backup/directory/path \
     --prefix backup-job-file-prefix \
     --key-store keys.p12 \
     --key-alias alias \
     --output-file /path/to/output.tsv \
     --at-epoch-seconds 123456
```

## Further reading

Please read more about configuring the BaRJ backup jobs [here](https://github.com/nagyesta/file-barj/wiki/Backup-job-configuration-tips).
