package org.phyloref.jphyloref;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.phyloref.jphyloref.commands.Command;
import org.phyloref.jphyloref.commands.ResolveCommand;
import org.phyloref.jphyloref.commands.TestCommand;
import org.phyloref.jphyloref.commands.WebserverCommand;
import org.phyloref.jphyloref.helpers.ReasonerHelper;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for JPhyloRef. Contains a list of Commands, as well as the code for determining which
 * Command to execute.
 */
public class JPhyloRef {
  /** Set up a logger to use for providing logging. */
  private static final Logger logger = LoggerFactory.getLogger(JPhyloRef.class);

  /** Version of JPhyloRef. */
  public static final String VERSION = "0.3";

  /** List of all commands included in JPhyloRef. */
  private List<Command> commands =
      Arrays.asList(
          new HelpCommand(), new TestCommand(), new WebserverCommand(), new ResolveCommand());

  /**
   * Interpret the command line arguments to determine which command to execute.
   *
   * @param args Command line arguments
   * @return The exit code to return to the shell (0 = success, other values = errors).
   */
  public int execute(String[] args) {
    // Prepare to parse command line arguments.
    Options opts = new Options();

    // Add global options.
    ReasonerHelper.addCommandLineOptions(opts);

    // Add per-command options.
    for (Command cmd : commands) {
      cmd.addCommandLineOptions(opts);
    }

    // Parse command line arguments.
    CommandLine cmdLine;
    try {
      cmdLine = new DefaultParser().parse(opts, args);
    } catch (ParseException ex) {
      logger.error("Could not parse command line options: {}", ex.toString());
      return 1;
    }

    // Are there any command line arguments?
    if (cmdLine.getArgList().isEmpty()) {
      // No command line arguments -- display help!
      HelpCommand help = new HelpCommand();
      return help.execute(cmdLine);
    } else {
      // Look for global options:
      //	--reasoner: Set the reasoner.

      // The first unprocessed argument should be the command.
      String command = cmdLine.getArgList().get(0);

      // Look for a Command with the name specified.
      for (Command cmd : commands) {
        if (cmd.getName().equalsIgnoreCase(command)) {
          // Found a match!
          cmd.execute(cmdLine);
          return 0;
        }
      }

      // Could not find any command.
      logger.error("Command '{}' has not been implemented.", command);
      return 1;
    }
  }

  /**
   * Main method for JPhyloRef. Creates the JPhyloRef instance and tells it to start processing the
   * command line arguments.
   *
   * @param args Command line arguments
   */
  public static void main(String[] args) {
    JPhyloRef jphyloref = new JPhyloRef();
    System.exit(jphyloref.execute(args));
  }

  /**
   * HelpCommand is a special command that lists help information on all currently implemented
   * Commands. You can activate it by running "jphyloref help" or by just entering "jphyloref"
   * without any command.
   */
  private class HelpCommand implements Command {
    /** This command is named "help", and so can be executed as "jphyloref help". */
    public String getName() {
      return "help";
    }

    /** Returns a description of the Help command. */
    public String getDescription() {
      return "Provides help on all jphyloref commands";
    }

    /** There are no command line options for the Help command. */
    public void addCommandLineOptions(Options opts) {}

    /** Display a list of Commands that can be executed on the command line. */
    public int execute(CommandLine cmdLine) {
      // Display version number.
      System.out.println(
          "JPhyloRef/"
              + JPhyloRef.VERSION
              + " OWLAPI/"
              + VersionInfo.getVersionInfo().getVersion());

      // Display a synopsis.
      System.out.println("Synopsis: jphyloref <command> <options>\n");

      // Display a list of currently included Commands.
      System.out.println("Where command is one of:");
      for (Command cmd : commands) {
        // Display the description of the command.
        System.out.println(" - " + cmd.getName() + ": " + cmd.getDescription());

        // Display all the command line options supported by that command.
        Options opts = new Options();
        cmd.addCommandLineOptions(opts);

        for (Option opt : opts.getOptions()) {
          String longOpt = "";
          if (opt.getLongOpt() != null) longOpt = ", " + opt.getLongOpt();

          System.out.println("    - " + opt.getOpt() + longOpt + ": " + opt.getDescription());
        }
      }

      // Display global options, including the list of possible reasoners.
      System.out.println("\nWe also accept some global options, including:");
      System.out.println(
          " --reasoner <reasonerName>: sets the reasoner name, which should be one of:");
      Map<String, OWLReasonerFactory> reasonerList = ReasonerHelper.getReasonerFactories();
      for (String name : reasonerList.keySet()) {
        OWLReasonerFactory factory = reasonerList.get(name);
        System.out.println(
            "    '" + name + "': " + ReasonerHelper.getReasonerNameAndVersion(factory));
      }

      // One final blank line, please.
      System.out.println("");

      return 0;
    }
  }
}
