package org.phyloref.jphyloref.helpers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/** A unit test for the OWLHelper class. */
@DisplayName("OWLHelper")
class OWLHelperTest {
  @Nested
  @DisplayName("has methods for reading labels that can")
  class ReadingLabelsTest {
    OWLOntologyManager ontologyManager;
    OWLOntology testOntology;

    /** Set up the test ontology with annotations involving labels */
    @BeforeEach
    void setupOntology() throws OWLOntologyCreationException {
      // Set up a set of axioms we will use to run these tests.
      ontologyManager = OWLManager.createOWLOntologyManager();
      OWLDataFactory df = ontologyManager.getOWLDataFactory();

      // A property for RDFS_LABEL.
      OWLAnnotationProperty RDFSLabelProperty =
          df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());

      // Set up some labels on a named individual.
      List<OWLAxiom> axioms = new ArrayList<>();
      IRI phyloref1IRI = IRI.create("http://example.org/phyloref1");

      // We set a label without a language tag (i.e. an xsd:string) as well as
      // several language-tagged labels (i.e. rdfs:langString).
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              RDFSLabelProperty, phyloref1IRI, df.getOWLLiteral("Label without a language tag")));
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              RDFSLabelProperty, phyloref1IRI, df.getOWLLiteral("Label in English", "en")));
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              RDFSLabelProperty, phyloref1IRI, df.getOWLLiteral("Etikett auf Englisch", "de")));
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              RDFSLabelProperty, phyloref1IRI, df.getOWLLiteral("अंग्रेजी में लेबल", "hi")));

      // Set up the test ontology.
      testOntology = ontologyManager.createOntology(new HashSet<>(axioms));
    }

    @Test
    @DisplayName("choose the right label by language")
    void canChooseLabelsByLanguage() {
      /*
       * One challenge with working with rdfs:labels is that they may be
       * language-tagged strings (i.e. rdfs:langString) or plain strings
       * (i.e. xsd:string).
       *
       * OWLHelper tries to help with this by using a simple algorithm:
       *  - A set of expected languages can be specified in order of priority.
       *    OWLHelper will return the labels in the first language that contains
       *    any labels at all.
       *  - If no language match or if the special language tag "" is used,
       *    labels specified as untagged language strings will be returned.
       *  - If no labels in the provided language and no untagged language
       *    strings are available, OWLHelper will return an empty list.
       *    It will never return a label in a language not explicitly requested.
       *
       * This method tests these possibilities.
       *
       */
      OWLDataFactory df = ontologyManager.getOWLDataFactory();
      OWLNamedIndividual phyloref =
          df.getOWLNamedIndividual(IRI.create("http://example.org/phyloref1"));

      // We have a helper method to retrieve English labels, as this is the most
      // common case.
      Set<String> englishLabels = OWLHelper.getLabelsInEnglish(phyloref, testOntology);
      assertEquals(1, englishLabels.size());
      assertTrue(
          englishLabels.contains("Label in English"),
          "Label 'Label in English' correctly identified.");

      // Look up Hindi or German names. Since Hindi is listed first, only the
      // Hindi label should be retrieved.
      Set<String> hindiGermanLabels =
          OWLHelper.getAnnotationLiteralsForEntity(
              testOntology,
              phyloref,
              df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
              Arrays.asList("hi", "de"));
      assertEquals(1, hindiGermanLabels.size());
      assertTrue(
          hindiGermanLabels.contains("अंग्रेजी में लेबल"),
          "Label 'अंग्रेजी में लेबल' correctly identified.");

      // Attempt to look up labels in Spanish. Since no such label exist, the
      // program will look for labels without an explicit language tag, and will
      // return that instead.
      Set<String> spanishLabels =
          OWLHelper.getAnnotationLiteralsForEntity(
              testOntology,
              phyloref,
              df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
              Arrays.asList("es"));
      assertEquals(1, spanishLabels.size());
      assertTrue(
          spanishLabels.contains("Label without a language tag"),
          "Label 'Label without a language tag' correctly identified.");

      // Look up an unlabeled entity (e.g. "http://example.org/phyloref2").
      // This should return an empty set.
      Set<String> unlabeledLabels =
          OWLHelper.getLabelsInEnglish(
              df.getOWLNamedIndividual(IRI.create("http://example.org/phyloref2")), testOntology);
      assertEquals(0, unlabeledLabels.size());
    }
  }
}
