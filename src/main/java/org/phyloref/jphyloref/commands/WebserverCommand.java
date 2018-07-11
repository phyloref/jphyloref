package org.phyloref.jphyloref.commands;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.phyloref.jphyloref.helpers.OWLHelper;
import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.tap4j.model.Comment;
import org.tap4j.model.Directive;
import org.tap4j.model.Plan;
import org.tap4j.model.TestResult;
import org.tap4j.model.TestSet;
import org.tap4j.producer.TapProducer;
import org.tap4j.producer.TapProducerFactory;
import org.tap4j.util.DirectiveValues;
import org.tap4j.util.StatusValues;
import uk.ac.manchester.cs.jfact.JFactReasoner;
import uk.ac.manchester.cs.jfact.kernel.options.JFactReasonerConfiguration;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * Sets up a webserver that allows reasoning over phyloreferences
 * over HTTP.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 *
 */
public class WebserverCommand implements Command {
    /**
     * This command is named "webserver". It should be
     * invoked as "java -jar jphyloref.jar webserver ..."
     */
    @Override
    public String getName() {
        return "webserver";
    }

    /**
     * A description of the Webserver command.
     *
     * @return A description of this command.
     */
    @Override
    public String getDescription() {
        return "Set up a webserver to allow reasoning of phyloreferences over HTTP.";
    }

    /**
     * Add command-line options specific to this command.
     *
     * @param opts The command-line options to modify for this command.
     */
    @Override
    public void addCommandLineOptions(Options opts) {
        opts.addOption(
            "h", "host", true,
            "The hostname to listen to HTTP connections on (default: 'localhost')"
        );
        opts.addOption(
            "p", "port", true,
            "The TCP port to listen to HTTP connections on (default: 8080)"
        );
    }

    /**
     * The webserver we set up.
     */
    class Webserver extends fi.iki.elonen.NanoHTTPD {
    	private final WebserverCommand cmd;

    	public Webserver(WebserverCommand cmd, String hostname, int port) throws IOException {
      		super(hostname, port);

      		this.cmd = cmd;

      		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
      		System.out.println("Webserver started. Try accessing it at http://" + hostname + ":" + port + "/");
    	}

    	@Override
    	public Response serve(IHTTPSession session) {
      		String path = session.getUri();
      		Map<String, List<String>> params = session.getParameters();

      		System.out.println(">> Request received to '" + path + "': " + params);

      		return newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "There is no resource at '" + path + "'\n");
    	}
    }

    /**
     * Set up a webserver to listen on the provided hostname and port (or their defaults).
     *
     * @param cmdLine The command line options provided to this command.
     */
    @Override
    public void execute(CommandLine cmdLine) throws RuntimeException {
        String hostname = cmdLine.getOptionValue("host", "localhost");
        String portString = cmdLine.getOptionValue("port", "8080");
        int port = Integer.parseInt(portString);

        try {
          	Webserver webserver = new Webserver(this, hostname, port);
          	while(webserver.isAlive()) {}
        } catch(IOException ex) {
            System.err.println("An error occurred while running webserver: " + ex);
        }
    }
}
