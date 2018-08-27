package org.phyloref.jphyloref.commands;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
import org.openrdf.model.impl.ValueFactoryImpl;
import org.phyloref.jphyloref.JPhyloRef;
import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.phyloref.jphyloref.helpers.ReasonerHelper;
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
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.rio.RioOWLRDFConsumerAdapter;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.AnonymousNodeChecker;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.util.VersionInfo;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * Sets up a webserver that allows reasoning over phyloreferences
 * over HTTP.
 *
 * At the moment, we implement a simple API, in which:
 *  /version:   returns a JSON object with information on the version of
 *              JPhyloRef and the reasoner being used.
 *  /reason:    expects a form upload with a 'jsonld' element containing a file
 *              upload of a JSON-LD file representing an ontology to test.
 *              This command will reason over the ontology and return a JSON
 *              dictionary, in which the keys are the IRIs for each phyloreference,
 *              and the values are lists of the IRIs of each node matched by that
 *              phyloreference.
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
      "The TCP port to listen to HTTP connections on (default: 34214)"
    );
  }

  /**
   * Set up a webserver to listen on the provided hostname and port (or their defaults).
   *
   * @param cmdLine The command line options provided to this command.
   */
  @Override
  public void execute(CommandLine cmdLine) throws RuntimeException {
      String hostname = cmdLine.getOptionValue("host", "localhost");
      String portString = cmdLine.getOptionValue("port", "34214");
      int port = Integer.parseInt(portString);

      try {
          Webserver webserver = new Webserver(this, hostname, port, cmdLine);
          while(webserver.isAlive()) {}
      } catch(IOException ex) {
          System.err.println("An error occurred while running webserver: " + ex);
      }
  }

  /**
   * The webserver we set up.
   */
  class Webserver extends fi.iki.elonen.NanoHTTPD {
    /**
     * We keep a copy of the WebserverCommand that invoked us, although we
     * don't use this for now.
     */
    private final WebserverCommand cmd;
    
    /**
     * The CommandLine used to invoke this webserver.
     */
    private final CommandLine cmdLine;
    
    /**
     * Create and start the webserver. It starts in another thread, so
     * execution will not stop.
     *
     * @param cmd The WebserverCommand that created this Webserver.
     * @param hostname The hostname under which this webserver should listen.
     * @param port The port this webserver should listen to.
     */
    public Webserver(WebserverCommand cmd, String hostname, int port, CommandLine cmdLine) throws IOException {
      super(hostname, port);

      this.cmd = cmd;
      this.cmdLine = cmdLine;

      start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
      System.out.println("Webserver started. Try accessing it at http://" + hostname + ":" + port + "/");
    }

    /**
     * Respond to a request for reasoning over a JSON-LD file (/reason).
     */
    public JSONObject serveReason(File jsonldFile) throws OWLOntologyCreationException, IOException {
      JSONObject response = new JSONObject("{'status': 'ok'}");

      // Prepare an ontology to fill with the provided JSON-LD file.
      OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

      // Is purl.obolibrary.org down? No worries, you can access local copies
      // of your ontologies in the 'ontologies/' folder.
      AutoIRIMapper mapper = new AutoIRIMapper(new File("ontologies"), true);
      System.err.println("Found local ontologies: " + mapper.getOntologyIRIs());
      manager.addIRIMapper(mapper);

      OWLOntology ontology = manager.createOntology();
      OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();

      // Set up a RioOWLRDFConsumerAdapter that will take in RDF and will
      // produce OWL to store in an ontology.
      AnonymousNodeChecker anonymousNodeChecker = new AnonymousNodeChecker() {
        /* Copied from https://github.com/owlcs/owlapi/blob/master/rio/src/main/java/org/semanticweb/owlapi/rio/RioParserImpl.java */

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

      RioOWLRDFConsumerAdapter rdfHandler = new RioOWLRDFConsumerAdapter(ontology, anonymousNodeChecker, config);
      rdfHandler.setOntologyFormat(new RDFJsonLDDocumentFormat());

      // Set up an RDF parser to read the JSON-LD file.
      RDFParser parser = Rio.createParser(RDFFormat.JSONLD);

      // We could connect the parser to the RioOWLRDFConsumerAdapter by saying:
      //  parser.setRDFHandler(rdfHandler);
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
      // (Tracked at https://github.com/phyloref/jphyloref/issues/11)
      //
      parser.setRDFHandler(new org.eclipse.rdf4j.rio.RDFHandler() {
        // Most of these methods just call the corresponding method on the
        // other rdfHandler.

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
        public void handleComment(String comment) throws RDFHandlerException {
          rdfHandler.handleComment(comment);
        }

        // The only exception to this are handleStatement(Statement), where we
        // need to translate from one Statement to the other. We do this by
        // translating subject, object and property using translateResource()
        // (to translate Resources) and translateValue() (to translate Values).

        @Override
        public void handleStatement(org.eclipse.rdf4j.model.Statement st) throws RDFHandlerException {
          ValueFactoryImpl svf = ValueFactoryImpl.getInstance();

          // System.out.println("Translating statement " + st);

          rdfHandler.handleStatement(svf.createStatement(
            translateResource(st.getSubject()),
            svf.createURI(st.getPredicate().stringValue()),
            translateValue(st.getObject())
          ));
        }

        /**
         * Translate a Resource from org.eclipse.rdf4j.model.Resource to
         * org.openrdf.model.Resource. We support two kinds of resources:
         * blank nodes (BNodes) and IRIs.
         */
        private org.openrdf.model.Resource translateResource(org.eclipse.rdf4j.model.Resource res) {
          ValueFactoryImpl svf = ValueFactoryImpl.getInstance();

          if(res instanceof org.eclipse.rdf4j.model.BNode) {
            org.eclipse.rdf4j.model.BNode bnode = (org.eclipse.rdf4j.model.BNode) res;

            return (Resource) svf.createBNode(bnode.getID());
          }

          if(res instanceof org.eclipse.rdf4j.model.IRI) {
            org.eclipse.rdf4j.model.IRI iri = (org.eclipse.rdf4j.model.IRI) res;

            return (Resource) svf.createURI(iri.stringValue());
          }

          throw new RuntimeException("Unknown resource type: " + res);
        }

        /**
         * Translate a Value from org.eclipse.rdf4j.model.Value to org.openrdf.model.Value.
         * We support three kinds of Values: blank nodes (BNodes), IRIs and
         * Literals. The first two are handled correctly, but literals are
         * always converted into strings, regardless of their actual data type.
         */
        private org.openrdf.model.Value translateValue(org.eclipse.rdf4j.model.Value value) {
          ValueFactoryImpl svf = ValueFactoryImpl.getInstance();

          if(value instanceof org.eclipse.rdf4j.model.BNode) {
            org.eclipse.rdf4j.model.BNode bnode = (org.eclipse.rdf4j.model.BNode) value;

            return (Value) svf.createBNode(bnode.getID());
          }

          if(value instanceof org.eclipse.rdf4j.model.IRI) {
            org.eclipse.rdf4j.model.IRI iri = (org.eclipse.rdf4j.model.IRI) value;

            return (Value) svf.createURI(iri.stringValue());
          }

          if(value instanceof org.eclipse.rdf4j.model.Literal) {
            org.eclipse.rdf4j.model.Literal literal = (org.eclipse.rdf4j.model.Literal) value;

            // Note that this converts literals that should be treated as integers,
            // doubles and so on will be converted into strings here.
            return (Value) svf.createLiteral(literal.stringValue(), svf.createURI(literal.getDatatype().stringValue()));
          }

          throw new RuntimeException("Unknown value type: " + value);
        }
      });

      // Setup ready; parse the file!
      // We could jsonldFile.toURI().toString() as the file IRI, but this points
      // to a temporary file on the server where the JSON-LD file was stored by
      // NanoHTTPD.
      parser.parse(new FileReader(jsonldFile), "http://example.org/jphyloref#");
      response.put("ontology", ontology.toString());

      // We have an ontology! Let's reason over it, and store the results as
      // a map of a list of node IRIs matched by each phyloref IRI.
      Map<String, Set<String>> nodesPerPhylorefAsString = new HashMap<>();

      // Set up and start the reasoner.
      OWLReasonerFactory factory = ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine);
      OWLReasoner reasoner = factory.createReasoner(ontology);

      // Go through all the phyloreferences, identifying all the nodes that have
      // matched to that phyloreference.
      for(OWLNamedIndividual phyloref: PhylorefHelper.getPhyloreferences(ontology, reasoner)) {
        IRI phylorefIRI = phyloref.getIRI();

        // Pun from the named individual phyloref to the class with the same IRI.
        OWLClass phylorefAsClass = manager.getOWLDataFactory().getOWLClass(phylorefIRI);

        // Identify all individuals contained in the class, but filter out everything that
        // is not an IRI_CDAO_NODE.
        Set<String> nodes = reasoner.getInstances(phylorefAsClass, false).getFlattened().stream()
          // This includes the phyloreference itself. We only want to
          // look at phylogeny nodes here. So, let's filter down to named
          // individuals that are asserted to be cdao:Nodes.
          .filter(indiv -> EntitySearcher.getTypes(indiv, ontology).stream().anyMatch(
            type -> (!type.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) ||
              type.asOWLClass().getIRI().equals(PhylorefHelper.IRI_CDAO_NODE)
          ))
          .map(indiv -> indiv.getIRI().toString())
          .collect(Collectors.toSet());

        nodesPerPhylorefAsString.put(phylorefIRI.toString(), nodes);
      }

      // Record phyloreferences and matching nodes in JSON response.
      response.put("phylorefs", nodesPerPhylorefAsString);
      return response;
    }

    /**
     * Respond to a request for the version (GET /version).
     */
    public JSONObject serveVersion() {
      JSONObject response = new JSONObject("{'status': 'ok'}");

      // Report OWL API version.
      String owlapiVersion = VersionInfo.getVersionInfo().getVersion();
      response.put("owlapiVersion", owlapiVersion);

      // Report reasoner version.
      String reasonerVersion = ReasonerHelper.getReasonerNameAndVersion(ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine));
      response.put("reasonerVersion", reasonerVersion);

      // Report JPhyloRef version.
      response.put("name",
        "JPhyloRef/" + JPhyloRef.VERSION +
        " OWLAPI/" + owlapiVersion + " " +
        reasonerVersion
      );
      response.put("version", JPhyloRef.VERSION);

      return response;
    }

    /**
     * Set up some common items when communicating with a browser.
     */
    public Response createResponse(IStatus status, JSONObject result) {
      Response response = newFixedLengthResponse(status, "application/json", result.toString());

      // Indicate that any resource can access this resource.
      response.addHeader("Access-Control-Allow-Origin", "*");

      return response;
    }

    /**
     * Respond to a request sent to this webserver.
     */
    @Override
    public Response serve(IHTTPSession session) {
      // Look for web forms in the body of the HTTP request.
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

      // Errors after this point respond with a JSON object.
      JSONObject response = new JSONObject("{'status': 'ok'}");

      // Get path and parameters.
      String path = session.getUri();
      Map<String, List<String>> params = session.getParameters();

      System.out.println(">> Request received to '" + path + "': " + params);

      if(path.equals("/reason")) {
        // We accept two kinds of inputs:
        //  1. A form containing 'jsonld' as a JSON-LD string to process.
        //  2. A form containing 'jsonldFile' as a JSON-LD file to read.
        String filename;
        File jsonldFile;

        try {
          if(params.containsKey("jsonldFile")) {
            filename = String.join("; ", params.get("jsonldFile"));
            jsonldFile = new File(files.get("jsonldFile"));
          } else if(params.containsKey("jsonld")) {
            jsonldFile = File.createTempFile("jphyloref", null);

            FileWriter writer = new FileWriter(jsonldFile);
            writer.write(String.join(";", params.get("jsonld")));
            writer.close();

          } else {
            response.put("status", "error");
            response.put("error", "Expected a form with a file upload in the 'jsonldFile' field or a JSON-LD string in the 'jsonld' field, but no such field was found");
            return createResponse(Status.BAD_REQUEST, response);
          }

          return createResponse(Status.OK, serveReason(jsonldFile));
        } catch (OWLOntologyCreationException | IOException ex) {
          response.put("status", "error");
          response.put("error", "Exception thrown: " + ex.getMessage());
          ex.printStackTrace();
          return createResponse(Status.INTERNAL_ERROR, response);
        }

      } else if(path.equals("/version")) {
        return createResponse(Status.OK, serveVersion());
      } else {
        response.put("status", "error");
        response.put("error", "Path '" + path + "' could not be found.");
        return createResponse(Status.NOT_FOUND, response);
      }
    }
  }
}
