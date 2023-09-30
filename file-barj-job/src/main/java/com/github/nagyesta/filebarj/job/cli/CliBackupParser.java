package com.github.nagyesta.filebarj.job.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.nio.file.Path;

/**
 * Parser class for the command line arguments.
 */
public class CliBackupParser extends GenericCliParser<BackupProperties> {

    private static final String THREADS = "threads";
    private static final String CONFIG = "config";

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
                        .longOpt(CONFIG)
                        .required()
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(Path.class)
                        .argName("config_file")
                        .desc("Defines where the configuration file can be found.").build());
    }

    /**
     * Creates a new {@link CliBackupParser} instance and sets the input arguments.
     *
     * @param args the command line arguments
     */
    public CliBackupParser(final String[] args) {
        super("java -jar file-barj-job.jar --" + Task.BACKUP.getCommand(), createOptions(), args, commandLine -> {
            final var config = Path.of(commandLine.getOptionValue(CONFIG)).toAbsolutePath();
            final var threads = Integer.parseInt(commandLine.getOptionValue(THREADS, "1"));
            return BackupProperties.builder()
                    .config(config)
                    .threads(threads)
                    .build();
        });
    }
}
