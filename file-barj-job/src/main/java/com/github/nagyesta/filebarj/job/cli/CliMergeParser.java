package com.github.nagyesta.filebarj.job.cli;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.Console;
import java.nio.file.Path;

/**
 * Parser class for the command line arguments of the merge task.
 */
@Slf4j
public class CliMergeParser extends CliICommonBackupFileParser<MergeProperties> {

    private static final String DELETE_OBSOLETE = "delete-obsolete";
    private static final String FROM_EPOCH_SECONDS = "from-epoch-seconds";
    private static final String TO_EPOCH_SECONDS = "to-epoch-seconds";

    /**
     * Creates a new {@link CliMergeParser} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public CliMergeParser(
            final String[] args,
            final Console console) {
        super(Task.MERGE, args, commandLine -> {
            final var deleteObsolete = Boolean.parseBoolean(commandLine.getOptionValue(DELETE_OBSOLETE, "false"));
            final var backupSource = Path.of(commandLine.getOptionValue(BACKUP_SOURCE)).toAbsolutePath();
            final var prefix = commandLine.getOptionValue(PREFIX);
            final var fromTime = Long.parseLong(commandLine.getOptionValue(FROM_EPOCH_SECONDS));
            final var toTime = Long.parseLong(commandLine.getOptionValue(TO_EPOCH_SECONDS));
            final var keyProperties = parseKeyProperties(console, commandLine);
            return MergeProperties.builder()
                    .deleteObsoleteFiles(deleteObsolete)
                    .backupSource(backupSource)
                    .keyProperties(keyProperties)
                    .prefix(prefix)
                    .fromTimeEpochSeconds(fromTime)
                    .toTimeEpochSeconds(toTime)
                    .build();
        });
    }

    @Override
    protected Options createOptions() {
        return super.createOptions()
                .addOption(Option.builder()
                        .longOpt(DELETE_OBSOLETE)
                        .numberOfArgs(1)
                        .argName("boolean")
                        .type(Boolean.class)
                        .required(false)
                        .desc("Allow deleting the backup files which are no longer needed, because their contents were merged.")
                        .build())
                .addOption(Option.builder()
                        .longOpt(FROM_EPOCH_SECONDS)
                        .numberOfArgs(1)
                        .argName("epoch_seconds")
                        .required(true)
                        .type(Long.class)
                        .desc("The date and time using UTC epoch seconds identifying the first increment we want to merge.")
                        .build())
                .addOption(Option.builder()
                        .longOpt(TO_EPOCH_SECONDS)
                        .numberOfArgs(1)
                        .argName("epoch_seconds")
                        .required(true)
                        .type(Long.class)
                        .desc("The date and time using UTC epoch seconds identifying the last increment we want to merge.")
                        .build());
    }
}
