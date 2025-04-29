package com.github.nagyesta.filebarj.job.cli;

import lombok.extern.slf4j.Slf4j;

import java.io.Console;
import java.nio.file.Path;

/**
 * Parser class for the command line arguments of the version inspection task.
 */
@Slf4j
public class CliInspectIncrementsParser extends CliICommonBackupFileParser<InspectIncrementsProperties> {

    /**
     * Creates a new {@link CliInspectIncrementsParser} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public CliInspectIncrementsParser(
            final String[] args,
            final Console console) {
        super(Task.INSPECT_INCREMENTS,  args, commandLine -> {
            final var prefix = commandLine.getOptionValue(PREFIX);
            final var keyProperties = parseKeyProperties(console, commandLine);
            final var backupSource = Path.of(commandLine.getOptionValue(BACKUP_SOURCE)).toAbsolutePath();
            return InspectIncrementsProperties.builder()
                    .keyProperties(keyProperties)
                    .backupSource(backupSource)
                    .prefix(prefix)
                    .build();
        });
    }
}
