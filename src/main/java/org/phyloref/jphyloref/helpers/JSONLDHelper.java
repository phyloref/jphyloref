package org.phyloref.jphyloref.helpers;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.openrdf.model.Resource;
import org.openrdf.model.Value;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.rio.RioOWLRDFConsumerAdapter;
import org.semanticweb.owlapi.util.AnonymousNodeChecker;

/**
 * JSONLDHelper provides methods to help read and process JSON-LD files.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class JSONLDHelper {
  /**
   * Create an RDFParser for JSON-LD files. When the parser's <tt>parse()</tt> method is called, its
   * contents will be added to the OWLOntology passed to this method.
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
    parser.setRDFHandler(
        new org.eclipse.rdf4j.rio.RDFHandler() {
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
          public void handleStatement(org.eclipse.rdf4j.model.Statement st)
              throws RDFHandlerException {
            ValueFactoryImpl svf = ValueFactoryImpl.getInstance();

            // System.out.println("Translating statement " + st);

            rdfHandler.handleStatement(
                svf.createStatement(
                    translateResource(st.getSubject()),
                    svf.createURI(st.getPredicate().stringValue()),
                    translateValue(st.getObject())));
          }

          /**
           * Translate a Resource from org.eclipse.rdf4j.model.Resource to
           * org.openrdf.model.Resource. We support two kinds of resources: blank nodes (BNodes) and
           * IRIs.
           */
          private org.openrdf.model.Resource translateResource(
              org.eclipse.rdf4j.model.Resource res) {
            ValueFactoryImpl svf = ValueFactoryImpl.getInstance();

            if (res instanceof org.eclipse.rdf4j.model.BNode) {
              org.eclipse.rdf4j.model.BNode bnode = (org.eclipse.rdf4j.model.BNode) res;

              return (Resource) svf.createBNode(bnode.getID());
            }

            if (res instanceof org.eclipse.rdf4j.model.IRI) {
              org.eclipse.rdf4j.model.IRI iri = (org.eclipse.rdf4j.model.IRI) res;

              return (Resource) svf.createURI(iri.stringValue());
            }

            throw new RuntimeException("Unknown resource type: " + res);
          }

          /**
           * Translate a Value from org.eclipse.rdf4j.model.Value to org.openrdf.model.Value. We
           * support three kinds of Values: blank nodes (BNodes), IRIs and Literals. The first two
           * are handled correctly, but literals are always converted into strings, regardless of
           * their actual data type.
           */
          private org.openrdf.model.Value translateValue(org.eclipse.rdf4j.model.Value value) {
            ValueFactoryImpl svf = ValueFactoryImpl.getInstance();

            if (value instanceof org.eclipse.rdf4j.model.BNode) {
              org.eclipse.rdf4j.model.BNode bnode = (org.eclipse.rdf4j.model.BNode) value;

              return (Value) svf.createBNode(bnode.getID());
            }

            if (value instanceof org.eclipse.rdf4j.model.IRI) {
              org.eclipse.rdf4j.model.IRI iri = (org.eclipse.rdf4j.model.IRI) value;

              return (Value) svf.createURI(iri.stringValue());
            }

            if (value instanceof org.eclipse.rdf4j.model.Literal) {
              org.eclipse.rdf4j.model.Literal literal = (org.eclipse.rdf4j.model.Literal) value;

              // Note that this converts literals that should be treated as integers,
              // doubles and so on will be converted into strings here.
              return (Value)
                  svf.createLiteral(
                      literal.stringValue(), svf.createURI(literal.getDatatype().stringValue()));
            }

            throw new RuntimeException("Unknown value type: " + value);
          }
        });

    return parser;
  }
}
