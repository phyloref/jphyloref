package org.phyloref.jphyloref.helpers;

import com.github.jsonldjava.core.DocumentLoader;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.RemoteDocument;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.rio.RioOWLRDFConsumerAdapter;
import org.semanticweb.owlapi.util.AnonymousNodeChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSONLDHelper provides methods to help read and process JSON-LD files.
 *
 * @author Gaurav Vaidya
 */
public class JSONLDHelper {
  private static final Logger logger = LoggerFactory.getLogger(JSONLDHelper.class);

  /**
   * Create an RDFParser for JSON-LD files. When the parser's <code>parse()</code> method is called,
   * its contents will be added to the OWLOntology passed to this method.
   *
   * @param ontology The ontology to create an RDF parser for.
   * @return An RDF Parser that can be used to read an OWL ontology from JSON-LD.
   */
  public static RDFParser createRDFParserForOntology(OWLOntology ontology) {
    // Set up a RioOWLRDFConsumerAdapter that will take in RDF and will
    // produce OWL to store in an ontology. This requires an anonymous node
    // checker, which has been copied from:
    // https://github.com/owlcs/owlapi/blob/master/rio/src/main/java/org/semanticweb/owlapi/rio/RioParserImpl.java
    AnonymousNodeChecker anonymousNodeChecker =
        new AnonymousNodeChecker() {
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

    // Create a RioOWLRDFConsumerAdapter, and use it as an RDFHandler.
    OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
    RioOWLRDFConsumerAdapter rdfHandler =
        new RioOWLRDFConsumerAdapter(ontology, anonymousNodeChecker, config);
    rdfHandler.setOntologyFormat(new RDFJsonLDDocumentFormat());

    // Set up an RDF parser to read the JSON-LD file.
    RDFParser parser = Rio.createParser(RDFFormat.JSONLD);
    parser.setRDFHandler(rdfHandler);

    // We get some indistinct errors if any of the context URLs in the JSON-LD file are
    // incorrect or inaccessible. However, we can set up our own document loader so we
    // can detect when this occurs.)
    parser.set(
        JSONLDSettings.DOCUMENT_LOADER,
        new DocumentLoader() {
          @Override
          public RemoteDocument loadDocument(String url) throws JsonLdError {
            try {
              return super.loadDocument(url);
            } catch (Exception err) {
              logger.error("Error occurred while loading document " + url + ": " + err);
              throw new JsonLdError(JsonLdError.Error.LOADING_REMOTE_CONTEXT_FAILED, url, err);
            }
          }
        });

    return parser;
  }
}
