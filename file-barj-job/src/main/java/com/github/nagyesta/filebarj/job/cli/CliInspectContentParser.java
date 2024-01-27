package com.github.nagyesta.filebarj.job.cli;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.Console;
import java.nio.file.Path;

/**
 * Parser class for the command line arguments of the inspect content task.
 */
@Slf4j
public class CliInspectContentParser extends CliICommonBackupFileParser<InspectIncrementContentsProperties> {

    private static final String OUTPUT_FILE = "output-file";
    private static final String AT_EPOCH_SECONDS = "at-epoch-seconds";

    /**
     * Creates a new {@link CliInspectContentParser} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public CliInspectContentParser(final String[] args, final Console console) {
        super(Task.RESTORE, args, commandLine -> {
            final var output = Path.of(commandLine.getOptionValue(OUTPUT_FILE, "backup_contents.csv"));
            final var atPointInTime = Long.parseLong(commandLine.getOptionValue(AT_EPOCH_SECONDS));
            final var backupSource = Path.of(commandLine.getOptionValue(BACKUP_SOURCE)).toAbsolutePath();
            final var prefix = commandLine.getOptionValue(PREFIX);
            final var keyProperties = parseKeyProperties(console, commandLine);
            return InspectIncrementContentsProperties.builder()
                    .outputFile(output)
                    .pointInTimeEpochSeconds(atPointInTime)
                    .backupSource(backupSource)
                    .keyProperties(keyProperties)
                    .prefix(prefix)
                    .build();
        });
    }

    @Override
    protected Options createOptions() {
        return super.createOptions()
                .addOption(Option.builder()
                        .longOpt(OUTPUT_FILE)
                        .hasArg(true)
                        .numberOfArgs(1)
                        .argName("output_file")
                        .type(Path.class)
                        .desc("The path where the output should be written. Default: backup_contents.csv").build())
                .addOption(Option.builder()
                        .longOpt(AT_EPOCH_SECONDS)
                        .numberOfArgs(1)
                        .argName("epoch_seconds")
                        .type(Long.class)
                        .desc("The date and time using UTC epoch seconds at which the content should be inspected.").build());
    }
}
