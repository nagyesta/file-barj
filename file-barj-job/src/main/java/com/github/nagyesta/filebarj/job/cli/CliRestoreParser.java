package com.github.nagyesta.filebarj.job.cli;

import com.github.nagyesta.filebarj.core.model.BackupPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.Console;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

/**
 * Parser class for the command line arguments of the restore task.
 */
@Slf4j
public class CliRestoreParser extends CliICommonBackupFileParser<RestoreProperties> {

    private static final String THREADS = "threads";
    private static final String DRY_RUN = "dry-run";
    private static final String TARGET = "target-mapping";
    private static final String DELETE_MISSING = "delete-missing";
    private static final String AT_EPOCH_SECONDS = "at-epoch-seconds";
    private static final String INCLUDED_PATH = "include-path";

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
            final var nowEpochSeconds = Instant.now().getEpochSecond() + "";
            final var atPointInTime = Long.parseLong(commandLine.getOptionValue(AT_EPOCH_SECONDS, nowEpochSeconds));
            final var includedPath = Optional.ofNullable(commandLine.getOptionValue(INCLUDED_PATH))
                    .map(BackupPath::ofPathAsIs)
                    .orElse(null);
            final var targets = new HashMap<BackupPath, Path>();
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
                        .forEach(s -> targets.put(BackupPath.ofPathAsIs(s[0]), Path.of(s[1]).toAbsolutePath()));
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
                    .pointInTimeEpochSeconds(atPointInTime)
                    .includedPath(includedPath)
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
                        .build())
                .addOption(Option.builder()
                        .longOpt(AT_EPOCH_SECONDS)
                        .numberOfArgs(1)
                        .argName("epoch_seconds")
                        .required(false)
                        .type(Long.class)
                        .desc("The date and time using UTC epoch seconds at which the content should be restored.")
                        .build())
                .addOption(Option.builder()
                        .longOpt(INCLUDED_PATH)
                        .numberOfArgs(1)
                        .argName("path_from_backup")
                        .required(false)
                        .type(Path.class)
                        .desc("Path of the file or directory which should be restored from the backup. Optional. If not provided,"
                                + " all files should be restored.")
                        .build());
    }
}
