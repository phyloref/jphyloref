package org.phyloref.jphyloref.commands;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.IStatus;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.json.JSONObject;
import org.phyloref.jphyloref.JPhyloRef;
import org.phyloref.jphyloref.helpers.JSONLDHelper;
import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.phyloref.jphyloref.helpers.ReasonerHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.util.VersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up a webserver that allows reasoning over phyloreferences over HTTP.
 *
 * <p>At the moment, we implement a simple API, in which: /version: returns a JSON object with
 * information on the version of JPhyloRef and the reasoner being used. /reason: expects a form
 * upload with a 'jsonld' element containing a file upload of a JSON-LD file representing an
 * ontology to test. This command will reason over the ontology and return a JSON dictionary, in
 * which the keys are the IRIs for each phyloreference, and the values are lists of the IRIs of each
 * node matched by that phyloreference.
 *
 * @author Gaurav Vaidya
 */
public class WebserverCommand implements Command {
  /** Set up a logger to use for providing logging. */
  private static final Logger logger = LoggerFactory.getLogger(WebserverCommand.class);

  /**
   * This command is named "webserver". It should be invoked as "java -jar jphyloref.jar webserver
   * ..."
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
        "h", "host", true, "The hostname to listen to HTTP connections on (default: 'localhost')");
    opts.addOption(
        "p", "port", true, "The TCP port to listen to HTTP connections on (default: 34214)");
  }

  /**
   * Set up a webserver to listen on the provided hostname and port (or their defaults).
   *
   * @param cmdLine The command line options provided to this command.
   */
  @Override
  public int execute(CommandLine cmdLine) throws RuntimeException {
    String hostname = cmdLine.getOptionValue("host", "localhost");
    String portString = cmdLine.getOptionValue("port", "34214");
    int port = Integer.parseInt(portString);

    try {
      Webserver webserver = new Webserver(this, hostname, port, cmdLine);
      while (webserver.isAlive()) {}
    } catch (IOException ex) {
      logger.error("An error occurred while running webserver: {}", ex.toString());
    }

    return 0;
  }

  /** The webserver we set up. */
  class Webserver extends fi.iki.elonen.NanoHTTPD {
    /**
     * We keep a copy of the WebserverCommand that invoked us, although we don't use this for now.
     */
    private final WebserverCommand cmd;

    /** The CommandLine used to invoke this webserver. */
    private final CommandLine cmdLine;

    /**
     * Create and start the webserver. It starts in another thread, so execution will not stop.
     *
     * @param cmd The WebserverCommand that created this Webserver.
     * @param hostname The hostname under which this webserver should listen.
     * @param port The port this webserver should listen to.
     */
    public Webserver(WebserverCommand cmd, String hostname, int port, CommandLine cmdLine)
        throws IOException {
      super(hostname, port);

      this.cmd = cmd;
      this.cmdLine = cmdLine;

      start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
      logger.info(
          "Webserver started with reasoner {}. Try accessing it at http://{}:{}/",
          ReasonerHelper.getReasonerNameAndVersion(
              ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine)),
          hostname,
          port);
    }

    /** Respond to a request for reasoning over a JSON-LD file (/reason). */
    public JSONObject serveReason(File jsonldFile)
        throws OWLOntologyCreationException, RDFParseException, IOException {
      JSONObject response = new JSONObject("{'status': 'ok'}");

      // Prepare an ontology to fill with the provided JSON-LD file.
      OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

      // Is purl.obolibrary.org down? No worries, you can access local copies
      // of your ontologies in the 'ontologies/' folder.
      AutoIRIMapper mapper = new AutoIRIMapper(new File("ontologies"), true);
      logger.info("Found local ontologies: {}", mapper.getOntologyIRIs());
      manager.addIRIMapper(mapper);

      // Setup ready; parse the file!
      // We could jsonldFile.toURI().toString() as the file IRI, but this points
      // to a temporary file on the server where the JSON-LD file was stored by
      // NanoHTTPD.
      //
      // The JSON-LD loader needs a default URI prefix. The input JSON-LD ontology
      // will usually provide one in the '@id' field, but if not, we set up a default
      // URI prefix here. If that prefix appears in either node URIs or phyloref URIs,
      // we will strip it later -- that way, a JSON-LD ontology without a base URI
      // (i.e. all of whose URIs are local to the document itself) will produce
      // results with local URIs as well.
      String DEFAULT_URI_PREFIX = "http://example.org/jphyloref";
      OWLOntology ontology = manager.createOntology();
      RDFParser parser = JSONLDHelper.createRDFParserForOntology(ontology);
      parser.parse(new FileReader(jsonldFile), DEFAULT_URI_PREFIX);
      response.put("ontology", ontology.toString());

      // We have an ontology! Let's reason over it, and store the results as
      // a map of a list of node IRIs matched by each phyloref IRI.
      Map<String, Set<String>> nodesPerPhylorefAsString = new HashMap<>();

      // Set up and start the reasoner.
      OWLReasonerFactory factory = ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine);
      OWLReasoner reasoner = factory.createReasoner(ontology);

      // Go through all the phyloreferences, identifying all the nodes that have
      // matched to that phyloreference.
      for (OWLClass phyloref : PhylorefHelper.getPhyloreferences(ontology, reasoner)) {
        IRI phylorefIRI = phyloref.getIRI();

        // Identify all individuals contained in this phyloref class, but filter out
        // everything that is not an IRI_CDAO_NODE.
        Set<String> nodes =
            PhylorefHelper.getNodesInClass(phyloref, ontology, reasoner)
                .stream()
                .map(indiv -> indiv.getIRI().toString())
                // Strip the default prefix on the node URI if present.
                .map(iri -> iri.replaceFirst("^" + DEFAULT_URI_PREFIX, ""))
                .collect(Collectors.toSet());

        // Strip the default prefix on the phyloref URI if present.
        String nodeURI = phylorefIRI.toString();
        nodeURI = nodeURI.replaceFirst("^" + DEFAULT_URI_PREFIX, "");

        nodesPerPhylorefAsString.put(nodeURI, nodes);
      }

      // Dispose the reasoner.
      reasoner.dispose();

      // Log reasoning results.
      logger.info("Phyloreferencing reasoning results: {}", nodesPerPhylorefAsString);

      // Record phyloreferences and matching nodes in JSON response.
      response.put("phylorefs", nodesPerPhylorefAsString);
      return response;
    }

    /** Respond to a request for the version (GET /version). */
    public JSONObject serveVersion() {
      JSONObject response = new JSONObject("{'status': 'ok'}");

      // Report OWL API version.
      String owlapiVersion = VersionInfo.getVersionInfo().getVersion();
      response.put("owlapiVersion", owlapiVersion);

      // Report reasoner version.
      String reasonerVersion =
          ReasonerHelper.getReasonerNameAndVersion(
              ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine));
      response.put("reasonerVersion", reasonerVersion);

      // Report JPhyloRef version.
      response.put(
          "name",
          "JPhyloRef/" + JPhyloRef.VERSION + " OWLAPI/" + owlapiVersion + " " + reasonerVersion);
      response.put("version", JPhyloRef.VERSION);

      return response;
    }

    /** Set up some common items when communicating with a browser. */
    public Response createResponse(IStatus status, JSONObject result) {
      Response response = newFixedLengthResponse(status, "application/json", result.toString());

      // Indicate that any resource can access this resource.
      response.addHeader("Access-Control-Allow-Origin", "*");

      return response;
    }

    /** Respond to a request sent to this webserver. */
    @Override
    public Response serve(IHTTPSession session) {
      // Look for web forms in the body of the HTTP request.
      Map<String, String> files = new HashMap<>();
      if (session.getMethod().equals(Method.PUT) || session.getMethod().equals(Method.POST)) {
        try {
          session.parseBody(files);
        } catch (IOException ex) {
          return newFixedLengthResponse(
              Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server threw IOException: " + ex);
        } catch (ResponseException re) {
          return newFixedLengthResponse(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
        }
      }

      // Errors after this point respond with a JSON object.
      JSONObject response = new JSONObject("{'status': 'ok'}");

      // Get path and parameters.
      String path = session.getUri();
      Map<String, List<String>> params = session.getParameters();

      logger.info(">> Request received to '{}': {}", path, params);

      if (path.equals("/reason")) {
        // If it is an OPTIONS request, it's probably someone wanting a
        // pre-flight CORS request. Let's give them that.
        if (session.getMethod().equals(Method.OPTIONS)) {
          Response preflightResponse =
              newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Options");

          // Indicate that any resource can access this resource.
          preflightResponse.addHeader("Access-Control-Allow-Origin", "*");
          preflightResponse.addHeader("Access-Control-Allow-Methods", "POST");
          preflightResponse.addHeader("Access-Control-Allow-Headers", "x-hub-signature");

          return preflightResponse;
        }

        // We accept three kinds of inputs:
        //  1. A form containing 'jsonld' as a JSON-LD string to process.
        //  2. A form containing 'jsonldGzipped' as a Base64-encoded Gzipped string
        //     containing a JSON-LD string to process.
        //  3. A form containing 'jsonldFile' as a JSON-LD file to read.
        // For simplicity's sake, we convert all the possible forms into a
        // File for processing.
        try {
          File jsonldFile = null;

          if (params.containsKey("jsonldFile")) {
            // It's already a file!
            jsonldFile = new File(files.get("jsonldFile"));

          } else {
            // It's a string, which we need to write to a temporary file.
            String jsonld = null;

            if (params.containsKey("jsonld")) {
              // Read JSON-LD as string.
              jsonld = String.join("", params.get("jsonld"));

            } else if (params.containsKey("jsonldGzipped")) {
              // Read JSON-LD as Gzipped string.
              String jsonldgzBase64 = String.join("", params.get("jsonldGzipped"));

              // Convert to a byte stream using Base64.
              byte[] jsonldGzipped = Base64.getDecoder().decode(jsonldgzBase64);

              // Convert unzip byte stream.
              GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(jsonldGzipped));

              // Read unzipped byte stream as a UTF-8 string, which has to be done line by line.
              BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, "UTF-8"));
              StringBuffer jsonldStringBuffer = new StringBuffer();

              String buffer;
              while ((buffer = reader.readLine()) != null) {
                jsonldStringBuffer.append(buffer);
              }

              // Store UTF-8 string for further processing.
              jsonld = jsonldStringBuffer.toString();
            } else {
              response.put("status", "error");
              response.put(
                  "error",
                  "Expected a form with a file upload in the 'jsonldFile' field or a JSON-LD string in the 'jsonld' or 'jsonldGzipped' field, but no such field was found");
              return createResponse(Status.BAD_REQUEST, response);
            }

            // Store the JSON-LD into a file for further processing.
            if (jsonld != null) {
              jsonldFile = File.createTempFile("jphyloref", null);
              FileWriter writer = new FileWriter(jsonldFile);
              writer.write(String.join(";", jsonld));
              writer.close();
            }
          }

          // Process JSON-LD file and return response.
          return createResponse(Status.OK, serveReason(jsonldFile));

        } catch (OWLOntologyCreationException | RDFParseException | IOException ex) {
          response.put("status", "error");
          response.put("error", "Exception thrown: " + ex.getMessage());
          ex.printStackTrace();
          return createResponse(Status.INTERNAL_ERROR, response);
        }

      } else if (path.equals("/version")) {
        return createResponse(Status.OK, serveVersion());
      } else {
        response.put("status", "error");
        response.put("error", "Path '" + path + "' could not be found.");
        return createResponse(Status.NOT_FOUND, response);
      }
    }
  }
}
