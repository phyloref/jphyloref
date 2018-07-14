package org.phyloref.jphyloref.commands;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.json.JSONObject;
import org.openrdf.model.Resource;
import org.openrdf.model.Value;
import org.openrdf.model.impl.SimpleValueFactory;
import org.phyloref.jphyloref.JPhyloRef;
import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.rio.RioOWLRDFConsumerAdapter;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.AnonymousNodeChecker;
import org.semanticweb.owlapi.util.VersionInfo;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import uk.ac.manchester.cs.jfact.JFactReasoner;
import uk.ac.manchester.cs.jfact.kernel.options.JFactReasonerConfiguration;

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
      		// Is there content in the body?
    		Map<String, String> files = new HashMap<>();
    		if(session.getMethod().equals(Method.PUT) || session.getMethod().equals(Method.POST)) {
    			try {
    				session.parseBody(files);
    			} catch(IOException ex) {
    				return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server threw IOException: " + ex);
    			} catch(ResponseException re) {
    				return newFixedLengthResponse(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
    			}
    		}

      		// Get path and parameters.
    		String path = session.getUri();
      		Map<String, List<String>> params = session.getParameters();

      		System.out.println(">> Request received to '" + path + "': " + params);

  			  JSONObject response = new JSONObject("{'status': 'ok'}");

  			if(path.equals("/test")) {
  				// If there are multiple 'jsonld' objects, we only read the first one.
  				String filename = String.join("; ", params.get("jsonld"));
  				File jsonldFile = new File(files.get("jsonld"));

  				// Is there is a readable file on the file path?
  				if(jsonldFile == null || !jsonldFile.canRead()) {
  					response.put("status", "error");
  					response.put("error", "Expected a form with a file upload in the 'jsonld' field, but no such field was found");
  				}

  				// We have a readable file! But is it JSON-LD?
  				try {
  					OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
  					OWLOntology ontology = manager.createOntology();
  					OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
  					AnonymousNodeChecker anonymousNodeChecker = new AnonymousNodeChecker() {
  						/* Stolen from https://github.com/owlcs/owlapi/blob/master/rio/src/main/java/org/semanticweb/owlapi/rio/RioParserImpl.java */

  						private boolean isAnonymous(String iri) {
  							return iri.startsWith("_:");
  						}

						@Override
						public boolean isAnonymousSharedNode(String iri) {
							return isAnonymous(iri);
						}

						@Override
						public boolean isAnonymousNode(String iri) {
							return isAnonymous(iri);
						}

						@Override
						public boolean isAnonymousNode(IRI iri) {
							return isAnonymous(iri.toString());
						}
					};

					RDFParser parser = Rio.createParser(RDFFormat.JSONLD);
					RioOWLRDFConsumerAdapter rdfHandler = new RioOWLRDFConsumerAdapter(ontology, anonymousNodeChecker, config);
					System.err.println("Created adapter of class " + rdfHandler.getClass() + ": " + rdfHandler.toString());

					rdfHandler.setOntologyFormat(new RDFJsonLDDocumentFormat());

          // We would like to use:
          //  parser.setRDFHandler(adapter);
          // Or alternatively:
          //  RioJsonLDParserFactory factory = new RioJsonLDParserFactory();
          //  factory.createParser().parse(new FileDocumentSource(jsonldFile), ontology, config);
          //
          // Unfortunately, RioOWLRDFConsumerAdapter implements org.openrdf.rio.RDFHandler
          // while the JSON-LD parser expects org.eclipse.rdf4j.rio.RDFHandler. I've tried
          // adding https://mvnrepository.com/artifact/org.openrdf.sesame/sesame-rio-jsonld,
          // as version 2.9.0, 4.0.2 and 4.1.2, but neither version registers as a JSON-LD
          // handler, giving the following error message:
          //	Caused by: org.openrdf.rio.UnsupportedRDFormatException: Did not recognise RDF
          //	format object JSON-LD (mimeTypes=application/ld+json; ext=jsonld)
          // So as to keep moving, I've written a very hacky translator from
          // an org.openrdf.rio.RDFHandler to an org.eclipse.rdf4j.rio.RDFHandler.
          //
					parser.setRDFHandler(new org.eclipse.rdf4j.rio.RDFHandler() {
						@Override
						public void startRDF() throws RDFHandlerException {
							rdfHandler.startRDF();
						}

						@Override
						public void endRDF() throws RDFHandlerException {
							rdfHandler.endRDF();
						}

						@Override
						public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
							rdfHandler.handleNamespace(prefix, uri);
						}

						@Override
						public void handleStatement(org.eclipse.rdf4j.model.Statement st) throws RDFHandlerException {
							SimpleValueFactory svf = SimpleValueFactory.getInstance();

							System.err.println("Translating statement " + st);

							rdfHandler.handleStatement(svf.createStatement(
								translateResource(st.getSubject()),
								svf.createIRI(st.getPredicate().stringValue()),
								translateValue(st.getObject())
							));
						}

						private org.openrdf.model.Value translateValue(org.eclipse.rdf4j.model.Value value) {
							SimpleValueFactory svf = SimpleValueFactory.getInstance();

							if(value instanceof org.eclipse.rdf4j.model.BNode) {
								org.eclipse.rdf4j.model.BNode bnode = (org.eclipse.rdf4j.model.BNode) value;

								return (Value) svf.createBNode(bnode.getID());
							}

							if(value instanceof org.eclipse.rdf4j.model.IRI) {
								org.eclipse.rdf4j.model.IRI iri = (org.eclipse.rdf4j.model.IRI) value;

								return (Value) svf.createIRI(iri.stringValue());
							}

							if(value instanceof org.eclipse.rdf4j.model.Literal) {
								org.eclipse.rdf4j.model.Literal literal = (org.eclipse.rdf4j.model.Literal) value;

								return (Value) svf.createLiteral(literal.stringValue(), svf.createIRI(literal.getDatatype().stringValue()));
							}

							throw new RuntimeException("Unknown value type: " + value);
						}

						private org.openrdf.model.Resource translateResource(org.eclipse.rdf4j.model.Resource res) {
							SimpleValueFactory svf = SimpleValueFactory.getInstance();

							if(res instanceof org.eclipse.rdf4j.model.BNode) {
								org.eclipse.rdf4j.model.BNode bnode = (org.eclipse.rdf4j.model.BNode) res;

								return (Resource) svf.createBNode(bnode.getID());
							}

							if(res instanceof org.eclipse.rdf4j.model.IRI) {
								org.eclipse.rdf4j.model.IRI iri = (org.eclipse.rdf4j.model.IRI) res;

								return (Resource) svf.createIRI(iri.stringValue());
							}

							throw new RuntimeException("Unknown resource type: " + res);
						}

						@Override
						public void handleComment(String comment) throws RDFHandlerException {
							rdfHandler.handleComment(comment);
						}
					});

  				parser.parse(new FileReader(jsonldFile), "http://example.org/jphyloref#");
  				response.put("ontology", ontology.toString());
  				
  				// We have an ontology! Let's reason over it.
  				Map<String, Set<String>> nodesPerPhylorefAsString = new HashMap<>();
  				
  				JFactReasonerConfiguration jfactConfig = new JFactReasonerConfiguration();
  				JFactReasoner reasoner = new JFactReasoner(ontology, jfactConfig, BufferingMode.BUFFERING);
  				for(OWLNamedIndividual phyloref: PhylorefHelper.getPhyloreferences(ontology, reasoner)) {
  					IRI phylorefIRI = phyloref.getIRI();
  					
  		            OWLClass phylorefAsClass = manager.getOWLDataFactory().getOWLClass(phylorefIRI);
  					Set<String> nodes = reasoner.getInstances(phylorefAsClass, false).entities()
		                // This includes the phyloreference itself. We only want to
		                // look at phylogeny nodes here. So, let's filter down to named
		                // individuals that are asserted to be cdao:Nodes.
		                .filter(indiv -> EntitySearcher.getTypes(indiv, ontology).anyMatch(
		                    type -> (!type.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) ||
		                			type.asOWLClass().getIRI().equals(PhylorefHelper.IRI_CDAO_NODE)
		                ))
		                .map(indiv -> indiv.getIRI().getIRIString())
		                .collect(Collectors.toSet());
  					
  					System.err.println("Phyloref '" + phylorefIRI + "' matched nodes: " + nodes.toString());
  					
  					nodesPerPhylorefAsString.put(phylorefIRI.getIRIString(), nodes);
  				}
  				
  				// Record phyloreferences and matching nodes in JSON response.
  				response.put("phylorefs", nodesPerPhylorefAsString);

				} catch (OWLOntologyCreationException | IOException ex) {
					response.put("status", "error");
					response.put("error", "Exception thrown: " + ex.getMessage());
					ex.printStackTrace();
					return newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", response.toString());
				}

  				return newFixedLengthResponse(Status.OK, "application/json", response.toString());

  			} else if(path.equals("/version")) {
        			String owlapiVersion = VersionInfo.getVersionInfo().getVersion();

        			response.put("name", "JPhyloRef/" + JPhyloRef.VERSION + " OWLAPI/" + owlapiVersion);
        			response.put("version", JPhyloRef.VERSION);
        			response.put("owlapiVersion", owlapiVersion);
        			return newFixedLengthResponse(Status.OK, "application/json", response.toString());
      		} else {
        			response.put("status", "error");
        			response.put("error", "Path '" + path + "' could not be found.");
        			return newFixedLengthResponse(Status.NOT_FOUND, "application/json", response.toString());
      		}
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
