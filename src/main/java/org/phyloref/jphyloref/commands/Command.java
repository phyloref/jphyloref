package org.phyloref.jphyloref.commands;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

/**
 * Commands provide commands for jphyloref to run. These are usually in the form 'jphyloref
 * <em>command</em> ...'
 *
 * @author Gaurav Vaidya
 */
public interface Command {
  /**
   * The name of this command. The command is usually invoked as "jphyloref command ...".
   *
   * @return The name of this command.
   */
  public String getName();

  /**
   * A one-line description of this command.
   *
   * @return A one-line description of this command as a String.
   */
  public String getDescription();

  /** A list of valid command line options. These should be added to the provided Options object. */
  public void addCommandLineOptions(Options opts);

  /**
   * Execute this command with the provided command line options, and provide an exit value to
   * return to the operating system.
   *
   * @param cmdLine The CommandLine to be executed.
   */
  public int execute(CommandLine cmdLine);
}
