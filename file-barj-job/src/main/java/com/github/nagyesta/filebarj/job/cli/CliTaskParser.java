package com.github.nagyesta.filebarj.job.cli;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;

import java.util.Arrays;

/**
 * Parser class for the command line arguments.
 */
public class CliTaskParser extends GenericCliParser<Task> {

    private static final String DASHES = "--";

    /**
     * Creates a new {@link CliTaskParser} instance and sets the input arguments.
     *
     * @param args the command line arguments
     */
    public CliTaskParser(final String[] args) {
        super("java -jar file-barj-job.jar", args, commandLine -> Arrays.stream(Task.values())
                .filter(task -> (DASHES + task.getCommand()).equals(args[0]))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matching task found: " + args[0])));
    }

    @Override
    protected Options createOptions() {
        final var group = new OptionGroup().addOption(Option.builder()
                        .longOpt(Task.BACKUP.getCommand())
                        .desc("Create a backup.").build())
                .addOption(Option.builder()
                        .longOpt(Task.RESTORE.getCommand())
                        .desc("Restore a backup.").build())
                .addOption(Option.builder()
                        .longOpt(Task.MERGE.getCommand())
                        .desc("Merge increments of a backup.").build())
                .addOption(Option.builder()
                        .longOpt(Task.GEN_KEYS.getCommand())
                        .desc("Generates a key pair for the encryption.").build())
                .addOption(Option.builder()
                        .longOpt(Task.INSPECT_CONTENT.getCommand())
                        .desc("Inspects the available backup increments of a backup.").build())
                .addOption(Option.builder()
                        .longOpt(Task.INSPECT_INCREMENTS.getCommand())
                        .desc("Inspects the contents of a backup increment.").build());
        group.setRequired(true);
        return new Options().addOptionGroup(group);
    }
}
