package com.github.nagyesta.filebarj.job.cli;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Parser class for the command line arguments.
 */
@Slf4j
public class CliRestoreParser extends GenericCliParser<RestoreProperties> {

    private static final String THREADS = "threads";
    private static final String DRY_RUN = "dry-run";
    private static final String BACKUP_SOURCE = "backup-source";
    private static final String KEY_STORE = "key-store";
    private static final String KEY_ALIAS = "key-alias";
    private static final String PREFIX = "prefix";
    private static final String TARGET = "target-mapping";
    private static final String DELETE_MISSING = "delete-missing";

    private static Options createOptions() {
        return new Options()
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
                        .longOpt(BACKUP_SOURCE)
                        .required()
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(Path.class)
                        .argName("backup_dir")
                        .desc("Defines where the backup files can be found.").build())
                .addOption(Option.builder()
                        .longOpt(KEY_STORE)
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(Path.class)
                        .argName("p12_store")
                        .desc("Defines where the P12 key store can be found. Required only if the backup was encrypted.")
                        .build())
                .addOption(Option.builder()
                        .longOpt(KEY_ALIAS)
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(String.class)
                        .argName("alias")
                        .desc("The alias of the key inside the P12 store. Default: default").build())
                .addOption(Option.builder()
                        .longOpt(PREFIX)
                        .required()
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(String.class)
                        .argName("file_name_prefix")
                        .desc("Defines the prefix of the backup files inside the backup directory.").build())
                .addOption(Option.builder()
                        .longOpt(TARGET)
                        .hasArgs()
                        .type(String.class)
                        .argName("from_dir=to_dir")
                        .desc("Defines where the files should be restored to by defining mappings that can specify which from_dir"
                                + " of the backup source should be restored to which to_dir (as if the two were equivalent). Optional.")
                        .build());
    }

    /**
     * Creates a new {@link CliRestoreParser} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public CliRestoreParser(final String[] args, final Console console) {
        super("java -jar file-barj-job.jar --" + Task.RESTORE.getCommand(), createOptions(), args, commandLine -> {
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
            KeyStoreProperties keyProperties = null;
            if (commandLine.hasOption(KEY_STORE)) {
                final var keyStore = Path.of(commandLine.getOptionValue(KEY_STORE)).toAbsolutePath();
                final var keyAlias = commandLine.getOptionValue(KEY_ALIAS, "default");
                final var password = console.readPassword("Enter password for the key store: ");
                keyProperties = KeyStoreProperties.builder()
                        .keyStore(keyStore)
                        .password(password)
                        .alias(keyAlias)
                        .build();
            }
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
}
