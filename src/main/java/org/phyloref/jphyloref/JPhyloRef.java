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
 * Main class for JPhyloRef. Figures out what the user 
 * wants us to do. 
 *
 */
public class JPhyloRef 
{
	public static final String VERSION = "0.0.1-SNAPSHOT";
	
	private List<Command> commands = Arrays.asList(
		new HelpCommand(),
		new TestCommand(),
		new ReasonCommand()
	);
	
    public static void main( String[] args )
    {
        JPhyloRef jphyloref = new JPhyloRef();
        jphyloref.execute(args);
    }
    
    public void execute(String[] args) {
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
        
        // The first unprocessed argument should be the command.
        if(cmdLine.getArgList().isEmpty()) {
        	// No command provided! Activate help.
        	HelpCommand help = new HelpCommand();
        	help.execute(cmdLine);
        } else {
        	String command = cmdLine.getArgList().get(0);
        	
        	for(Command cmd: commands) {
        		if(cmd.getName().equalsIgnoreCase(command)) {
        			// match!
        			cmd.execute(cmdLine);
        			System.exit(0);
        			return;
        		}
        	}
        	
        	System.err.println("Error: command '" + command + "' has not been implemented.");
        	System.exit(1);
        	return;
        }
    }
    
    private class HelpCommand implements Command {
		public String getName() { return "help"; }
		public String getDescription() { return "Provides help on all jphyloref commands"; }
		public void addCommandLineOptions(Options opts) { }
		public void execute(CommandLine cmdLine) {
			// No arguments are provided.
			
			System.err.println("Synopsis: jphyloref <command> <options>\n");
			
			System.err.println("Where command is one of:");
			for(Command cmd: commands) {
				System.err.println(" - " + cmd.getName() + ": " + cmd.getDescription());
				
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
