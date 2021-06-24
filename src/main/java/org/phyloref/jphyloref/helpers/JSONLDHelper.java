package org.phyloref.jphyloref.helpers;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.rio.RioOWLRDFConsumerAdapter;
import org.semanticweb.owlapi.util.AnonymousNodeChecker;

/**
 * JSONLDHelper provides methods to help read and process JSON-LD files.
 *
 * @author Gaurav Vaidya
 */
public class JSONLDHelper {
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

    return parser;
  }
}
