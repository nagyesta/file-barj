package com.github.nagyesta.filebarj.job.cli;

import lombok.Getter;
import org.apache.commons.cli.*;

import java.util.function.Function;

/**
 * Generic parser class for the command line arguments.
 *
 * @param <T> the type of the parsed data
 */
@Getter
public abstract class GenericCliParser<T> {

    private static final int MAX_WIDTH = 120;
    private T result;

    /**
     * Creates a new instance and sets the input arguments.
     *
     * @param command   the command
     * @param args      the command line arguments
     * @param evaluator the evaluation function
     */
    public GenericCliParser(final String command, final String[] args, final Function<CommandLine, T> evaluator) {
        final var parser = new DefaultParser();
        final var options = createOptions();
        try {
            if (args == null || args.length == 0) {
                throw new ParseException("Missing command line arguments.");
            }
            final var commandLine = parser.parse(options, args);
            this.result = evaluator.apply(commandLine);
        } catch (final Exception e) {
            new HelpFormatter().printHelp(MAX_WIDTH, command, "\nERROR: " + e.getMessage() + "\n\n", options, "\n\n");
            throw new IllegalArgumentException("Failed to parse command line arguments: ", e);
        }
    }

    /**
     * Creates the options for the command line parser.
     *
     * @return the options
     */
    protected abstract Options createOptions();
}
