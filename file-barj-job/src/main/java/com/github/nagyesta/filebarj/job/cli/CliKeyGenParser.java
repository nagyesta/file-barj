package com.github.nagyesta.filebarj.job.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Parser class for the command line arguments.
 */
public class CliKeyGenParser extends GenericCliParser<KeyStoreProperties> {

    private static final String KEY_STORE = "key-store";
    private static final String KEY_ALIAS = "key-alias";

    /**
     * Creates a new {@link CliKeyGenParser} instance and sets the input arguments.
     *
     * @param args    the command line arguments
     * @param console the console we should use for password input
     */
    public CliKeyGenParser(final String[] args, final Console console) {
        super("java -jar file-barj-job.jar --" + Task.GEN_KEYS.getCommand(), args, commandLine -> {
            final var store = Path.of(commandLine.getOptionValue(KEY_STORE)).toAbsolutePath();
            final var alias = commandLine.getOptionValue(KEY_ALIAS, "default");
            final var password = console.readPassword("Enter password for the key store: ");
            final var passwordRepeat = console.readPassword("Repeat password for the key store: ");
            if (password == null || !Arrays.equals(password, passwordRepeat)) {
                throw new IllegalArgumentException("ERROR: Passwords do not match.");
            }
            return KeyStoreProperties.builder()
                    .keyStore(store)
                    .password(password)
                    .alias(alias)
                    .build();
        });
    }

    @Override
    protected Options createOptions() {
        return new Options()
                .addOption(Option.builder()
                        .longOpt(KEY_STORE)
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(Path.class)
                        .argName("p12_store")
                        .desc("Defines where the P12 key store can be found.").build())
                .addOption(Option.builder()
                        .longOpt(KEY_ALIAS)
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(String.class)
                        .argName("alias")
                        .desc("The alias of the key inside the P12 store. Default: default").build());
    }
}
