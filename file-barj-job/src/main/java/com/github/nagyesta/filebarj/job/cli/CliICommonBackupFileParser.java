package com.github.nagyesta.filebarj.job.cli;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jetbrains.annotations.Nullable;

import java.io.Console;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Parser class for the command line arguments of a generic task using backup files.
 *
 * @param <T> the type of the parsed properties
 */
@Slf4j
public class CliICommonBackupFileParser<T> extends GenericCliParser<T> {

    /**
     * The command line option for the backup source.
     */
    protected static final String BACKUP_SOURCE = "backup-source";
    /**
     * The command line option for the key store.
     */
    protected static final String KEY_STORE = "key-store";
    /**
     * The command line option for the key alias.
     */
    protected static final String KEY_ALIAS = "key-alias";
    /**
     * The command line option for the prefix.
     */
    protected static final String PREFIX = "prefix";

    /**
     * Creates a new {@link CliICommonBackupFileParser} instance and sets the input arguments.
     *
     * @param task      the task supported by this parser
     * @param args      the command line arguments
     * @param evaluator the evaluation function
     */
    public CliICommonBackupFileParser(final Task task, final String[] args, final Function<CommandLine, T> evaluator) {
        super("java -jar file-barj-job.jar --" + task.getCommand(), args, evaluator);
    }

    @Override
    protected Options createOptions() {
        return new Options()
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
                        .desc("Defines the prefix of the backup files inside the backup directory.").build());
    }

    @Nullable
    protected static KeyStoreProperties parseKeyProperties(final Console console, final CommandLine commandLine) {
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
        return keyProperties;
    }
}
