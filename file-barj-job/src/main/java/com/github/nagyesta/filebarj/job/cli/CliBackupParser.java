package com.github.nagyesta.filebarj.job.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.nio.file.Path;

/**
 * Parser class for the command line arguments of the backup task.
 */
public class CliBackupParser extends GenericCliParser<BackupProperties> {

    private static final String THREADS = "threads";
    private static final String CONFIG = "config";
    private static final String FORCE_FULL = "force-full-backup";

    /**
     * Creates a new {@link CliBackupParser} instance and sets the input arguments.
     *
     * @param args the command line arguments
     */
    public CliBackupParser(final String[] args) {
        super("java -jar file-barj-job.jar --" + Task.BACKUP.getCommand(), args, commandLine -> {
            final var config = Path.of(commandLine.getOptionValue(CONFIG)).toAbsolutePath();
            final var threads = Integer.parseInt(commandLine.getOptionValue(THREADS, "1"));
            final var forceFull = Boolean.parseBoolean(commandLine.getOptionValue(FORCE_FULL, "false"));
            return BackupProperties.builder()
                    .config(config)
                    .threads(threads)
                    .forceFullBackup(forceFull)
                    .build();
        });
    }

    @Override
    protected Options createOptions() {
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
                        .desc("Defines where the configuration file can be found.").build())
                .addOption(Option.builder()
                        .longOpt(FORCE_FULL)
                        .required(false)
                        .hasArg(true)
                        .numberOfArgs(1)
                        .type(Boolean.class)
                        .argName("boolean")
                        .desc("Forces the creation of a full backup if true.").build());
    }
}
