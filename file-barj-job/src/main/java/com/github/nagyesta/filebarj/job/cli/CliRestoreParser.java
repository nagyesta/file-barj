package com.github.nagyesta.filebarj.job.cli;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Parser class for the command line arguments of the restore task.
 */
@Slf4j
public class CliRestoreParser extends CliICommonBackupFileParser<RestoreProperties> {

    private static final String THREADS = "threads";
    private static final String DRY_RUN = "dry-run";
    private static final String TARGET = "target-mapping";
    private static final String DELETE_MISSING = "delete-missing";

    /**
     * Creates a new {@link CliRestoreParser} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public CliRestoreParser(final String[] args, final Console console) {
        super(Task.RESTORE, args, commandLine -> {
            final var threads = Integer.parseInt(commandLine.getOptionValue(THREADS, "1"));
            final var dryRun = Boolean.parseBoolean(commandLine.getOptionValue(DRY_RUN, "false"));
            final var deleteMissing = Boolean.parseBoolean(commandLine.getOptionValue(DELETE_MISSING, "false"));
            final var backupSource = Path.of(commandLine.getOptionValue(BACKUP_SOURCE)).toAbsolutePath();
            final var prefix = commandLine.getOptionValue(PREFIX);
            final var targets = new HashMap<Path, Path>();
            if (commandLine.hasOption(TARGET)) {
                final var mappings = commandLine.getOptionValues(TARGET);
                final var invalid = Arrays.stream(mappings).filter(m -> !m.matches("^[^=]+=[^=]+$"))
                        .toList();
                if (!invalid.isEmpty()) {
                    log.error("Invalid target mappings: \n    {}", String.join("\n    ", invalid));
                    throw new IllegalArgumentException("Invalid target mappings found.");
                }
                Arrays.stream(mappings)
                        .map(s -> s.split("="))
                        .forEach(s -> targets.put(Path.of(s[0]).toAbsolutePath(), Path.of(s[1]).toAbsolutePath()));
            }
            final var keyProperties = parseKeyProperties(console, commandLine);
            return RestoreProperties.builder()
                    .threads(threads)
                    .dryRun(dryRun)
                    .deleteFilesNotInBackup(deleteMissing)
                    .backupSource(backupSource)
                    .keyProperties(keyProperties)
                    .prefix(prefix)
                    .targets(targets)
                    .build();
        });
    }

    @Override
    protected Options createOptions() {
        return super.createOptions()
                .addOption(Option.builder()
                        .longOpt(THREADS)
                        .hasArg(true)
                        .numberOfArgs(1)
                        .argName("thread_number")
                        .type(Integer.class)
                        .desc("Sets the number of threads to use. Default: 1").build())
                .addOption(Option.builder()
                        .longOpt(DRY_RUN)
                        .numberOfArgs(1)
                        .argName("boolean")
                        .type(Boolean.class)
                        .desc("Only simulates file operations if provided.").build())
                .addOption(Option.builder()
                        .longOpt(DELETE_MISSING)
                        .numberOfArgs(1)
                        .argName("boolean")
                        .type(Boolean.class)
                        .desc("Allow deleting the files from the target directory that are matching the backup source patterns "
                                + "but are not present in the backup increment.").build())
                .addOption(Option.builder()
                        .longOpt(TARGET)
                        .hasArgs()
                        .type(String.class)
                        .argName("from_dir=to_dir")
                        .desc("Defines where the files should be restored to by defining mappings that can specify which from_dir"
                                + " of the backup source should be restored to which to_dir (as if the two were equivalent). Optional.")
                        .build());
    }
}
