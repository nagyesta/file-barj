![FileBarj](.github/assets/FileBarJ-logo-512.png)

[![GitHub license](https://img.shields.io/github/license/nagyesta/file-barj?color=informational)](https://raw.githubusercontent.com/nagyesta/file-barj/main/LICENSE)
[![Java version](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)](https://img.shields.io/badge/Java%20version-17-yellow?logo=java)
[![latest-release](https://img.shields.io/github/v/tag/nagyesta/file-barj?color=blue&logo=git&label=releases&sort=semver)](https://github.com/nagyesta/file-barj/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.nagyesta.file-barj/file-barj-app?logo=apache-maven)](https://search.maven.org/search?q=com.github.nagyesta.file-barj)
[![JavaCI](https://img.shields.io/github/actions/workflow/status/nagyesta/file-barj/gradle.yml?logo=github&branch=main)](https://github.com/nagyesta/file-barj/actions/workflows/gradle.yml)

[![code-climate-maintainability](https://img.shields.io/codeclimate/maintainability/nagyesta/file-barj?logo=code%20climate)](https://img.shields.io/codeclimate/maintainability/nagyesta/file-barj?logo=code%20climate)
[![code-climate-tech-debt](https://img.shields.io/codeclimate/tech-debt/nagyesta/file-barj?logo=code%20climate)](https://img.shields.io/codeclimate/tech-debt/nagyesta/file-barj?logo=code%20climate)
[![last_commit](https://img.shields.io/github/last-commit/nagyesta/file-barj?logo=git)](https://img.shields.io/github/last-commit/nagyesta/file-barj?logo=git)
[![badge-abort-mission-armed-green](https://raw.githubusercontent.com/nagyesta/abort-mission/wiki_assets/.github/assets/badge-abort-mission-armed-green.svg)](https://github.com/nagyesta/abort-mission)

File BaRJ (File Backup and Restore Java) is a multi-platform backup utility for files. It is intended to be a highly configurable tool
that can create secure backups of preconfigured files and folders and can be easily scheduled.

## Recommended use

### Warning!

> File BaRJ is a free tool that is provided "as is", **without warranty of any kind**. It might be the perfect tool you need, or leave you
> with gigabytes of encrypted hot mess instead of your precious data. By using it, you accept the risk of data loss (among others).

## Features

File BaRJ comes with the following features

- Full backups of folders or files
- Incremental backups of folders or files
- Change detection based on:
  - file size, 
  - last modification time, 
  - configurable hash algorithms
- Optional encryption using
  - an RSA key pair
  - a password
  - AES-256 per file and/or the backup archive
- Compression of the backup archive
- Backup archive splitting to configurable chunks
- Backup archive integrity checks
- Restore/unpack previous backup

## Quick start guide

### Startup parameters

TBD

# Limitations

TBD
