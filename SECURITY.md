# Security Policy

## Err on the safer side

As File BaRJ is intended to be a standalone backup utility. When using File BaRJ, you are responsible for securing the environment where
it is running, including the cryptographic keys or other sensitive materials which are used by File BaRj during the backup or restore
processes. The File BaRJ artifacts are only using the dependencies which are necessary for them to run, but despite our best efforts,
these dependencies might have vulnerabilities. We try to keep the dependency versions up-to-date by using automated tools for updating them
as well as regular release triggers. As a result, we aim to release an updated version each month.

When running the backup tool, File BaRj will expect that the configuration file is properly constructed and was not tampered with. You,
the end user, must ensure that this expectation is met.

## Supported Versions

The aim is to support our users as much as possible with security updates, backup and restore are sensitive topics after all. At the end
of the day, this is a hobby project which is maintained in my free time. So reality is that the latest version will be supported with 
security patches in case vulnerabilities are reported and everything else will be decided case by case.

[![Supported version](https://img.shields.io/github/v/tag/nagyesta/file-barj?color=green&logo=git&label=Supported%20version&sort=semver)](https://img.shields.io/github/v/tag/nagyesta/file-barj?color=green&logo=git&label=Supported%20version&sort=semver)

## Reporting a Vulnerability

In case you have found a vulnerability, please report an [issue here](https://github.com/nagyesta/file-barj/issues)

Thank you in advance!

## Vulnerability Response

Once a vulnerability is reported, I will try to fix it as soon as I can afford the time, preferably under less than 60 days from receiving a
valid security vulnerability report.

In case of vulnerable dependencies, response time depends on the release of the known safe/fixed dependency version as well. As long as 
there is no such available version, the update activity is considered to be blocked, therefore the normal response timeline does not apply.
