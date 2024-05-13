package com.github.nagyesta.filebarj.job.cli;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.Console;
import java.nio.file.Path;

/**
 * Parser class for the command line arguments of the version deletion task.
 */
@Slf4j
public class CliDeleteIncrementsParser extends CliICommonBackupFileParser<DeleteIncrementsProperties> {

    private static final String FROM_EPOCH_SECONDS = "from-epoch-seconds";

    /**
     * Creates a new {@link CliDeleteIncrementsParser} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public CliDeleteIncrementsParser(final String[] args, final Console console) {
        super(Task.DELETE_INCREMENTS,  args, commandLine -> {
            final var prefix = commandLine.getOptionValue(PREFIX);
            final var keyProperties = parseKeyProperties(console, commandLine);
            final var backupSource = Path.of(commandLine.getOptionValue(BACKUP_SOURCE)).toAbsolutePath();
            final var fromTime = Long.parseLong(commandLine.getOptionValue(FROM_EPOCH_SECONDS));
            return DeleteIncrementsProperties.builder()
                    .keyProperties(keyProperties)
                    .backupSource(backupSource)
                    .prefix(prefix)
                    .afterEpochSeconds(fromTime)
                    .build();
        });
    }

    @Override
    protected Options createOptions() {
        return super.createOptions()
                .addOption(Option.builder()
                        .longOpt(FROM_EPOCH_SECONDS)
                        .numberOfArgs(1)
                        .argName("epoch_seconds")
                        .required(true)
                        .type(Long.class)
                        .desc("The date and time using UTC epoch seconds identifying the first increment we want to delete.")
                        .build());
    }
}
