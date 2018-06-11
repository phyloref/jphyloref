package org.phyloref.jphyloref;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.phyloref.jphyloref.commands.Command;
import org.phyloref.jphyloref.commands.ReasonCommand;
import org.phyloref.jphyloref.commands.TestCommand;

/**
 * Main class for JPhyloRef. Contains a list of Commands,
 * as well as the code for determining which Command to
 * execute. 
 *
 */
public class JPhyloRef 
{
	/** Version of JPhyloRef */
	public static final String VERSION = "0.0.1-SNAPSHOT";
	
	/** List of all commands included in JPhyloRef */
	private List<Command> commands = Arrays.asList(
		new HelpCommand(),
		new TestCommand(),
		new ReasonCommand()
	);
	
	/**
	 * Interpret the command line arguments to determine which command
	 * to execute.
	 * 
	 * @param args Command line arguments
	 */
    public void execute(String[] args) {
    	// Display version information.
    	System.err.println("jphyloref/" + VERSION + "\n");
        
        // Prepare to parse command line arguments.
        Options opts = new Options();
        for(Command cmd: commands) {
        	cmd.addCommandLineOptions(opts);
        }
        
        // Parse command line arguments.
        CommandLine cmdLine;
        try {
        	cmdLine = new DefaultParser().parse(opts, args);
        } catch(ParseException ex) {
        	System.err.println("Could not parse command line options: " + ex);
        	System.exit(1);
        	return;
        }
        
        // Are there any command line arguments?
        if(cmdLine.getArgList().isEmpty()) {
        	// No command line arguments -- display help!
        	HelpCommand help = new HelpCommand();
        	help.execute(cmdLine);
        } else {
        	// The first unprocessed argument should be the command.
        	String command = cmdLine.getArgList().get(0);

        	// Look for a Command with the name specified. 
        	for(Command cmd: commands) {
        		if(cmd.getName().equalsIgnoreCase(command)) {
        			// Found a match!
        			cmd.execute(cmdLine);
        			System.exit(0);
        			return;
        		}
        	}
        	
        	// Could not find any command.
        	System.err.println("Error: command '" + command + "' has not been implemented.");
        	System.exit(1);
        	return;
        }
    }
    
	/** 
	 * Main method for JPhyloRef. Creates the JPhyloRef instance
	 * and tells it to start processing the command line arguments.
	 *  
	 * @param args Command line arguments
	 */
    public static void main( String[] args )
    {
        JPhyloRef jphyloref = new JPhyloRef();
        jphyloref.execute(args);
    }

    /**
     * HelpCommand is a special command that lists help information on all currently
     * implemented Commands. You can activate it by running "jphyloref help" or by
     * just entering "jphyloref" without any command.
     */
    private class HelpCommand implements Command {
    	/** This command is named "help", and so can be executed as "jphyloref help" */
		public String getName() { return "help"; }
		
		/** Returns a description of the Help command */
		public String getDescription() { return "Provides help on all jphyloref commands"; }
		
		/** There are no command line options for the Help command. */
		public void addCommandLineOptions(Options opts) { }
		
		/** Display a list of Commands that can be executed on the command line. */
		public void execute(CommandLine cmdLine) {
			// Display a synopsis.
			System.err.println("Synopsis: jphyloref <command> <options>\n");
			
			// Display a list of currently included Commands.
			System.err.println("Where command is one of:");
			for(Command cmd: commands) {
				// Display the description of the command.
				System.err.println(" - " + cmd.getName() + ": " + cmd.getDescription());
				
				// Display all the command line options supported by that command.
				Options opts = new Options();
				cmd.addCommandLineOptions(opts);
				
				for(Option opt: opts.getOptions()) {
					String longOpt = "";
					if(opt.getLongOpt() != null)
						longOpt = ", " + opt.getLongOpt();
					
					System.err.println("    - "
						+ opt.getOpt() + longOpt + ": "
						+ opt.getDescription()
					);
				}
			}
			
			// One final blank line, please.
			System.err.println("");
		}
    }
}
