package com.github.nagyesta.filebarj.job.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;

/**
 * Parser class for the command line arguments.
 */
public class CliTaskParser extends GenericCliParser<Task> {

    private  static final String DASHES = "--";

    private static Options createOptions() {
        final var group = new OptionGroup().addOption(Option.builder()
                        .longOpt(Task.BACKUP.getCommand())
                        .desc("Create a backup.").build())
                .addOption(Option.builder()
                        .longOpt(Task.RESTORE.getCommand())
                        .desc("Restore a backup.").build())
                .addOption(Option.builder()
                        .longOpt(Task.GEN_KEYS.getCommand())
                        .desc("Generates a key pair for the encryption.").build());
        group.setRequired(true);
        return new Options().addOptionGroup(group);
    }

    /**
     * Creates a new {@link CliTaskParser} instance and sets the input arguments.
     *
     * @param args the command line arguments
     */
    public CliTaskParser(final String[] args) {
        super("java -jar file-barj-job.jar", createOptions(), args, commandLine -> {
            if ((DASHES + Task.BACKUP.getCommand()).equals(args[0])) {
                return Task.BACKUP;
            } else if ((DASHES + Task.RESTORE.getCommand()).equals(args[0])) {
                return Task.RESTORE;
            } else if ((DASHES + Task.GEN_KEYS.getCommand()).equals(args[0])) {
                return Task.GEN_KEYS;
            } else {
                throw new IllegalArgumentException("No matching task found: " + args[0]);
            }
        });
    }
}
