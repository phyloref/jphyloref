package org.phyloref.jphyloref.helpers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import uk.ac.manchester.cs.jfact.JFactFactory;

/** A unit test for the PhylorefHelper class. */
@DisplayName("PhylorefHelper")
class PhylorefHelperTest {
  @Nested
  @DisplayName("has methods for retrieving lists of phylorefs that can")
  class PhylorefRetrievalTest {
    OWLOntologyManager ontologyManager;
    OWLOntology testOntology;

    /** Set up the test ontology with annotations involving labels */
    @BeforeEach
    void setupOntology() throws OWLOntologyCreationException {
      // Set up a set of axioms we will use to run these tests.
      ontologyManager = OWLManager.createOWLOntologyManager();
      OWLDataFactory df = ontologyManager.getOWLDataFactory();

      // Set up a phyloreference we can use for testing.
      List<OWLAxiom> axioms = new ArrayList<>();
      IRI phyloref1IRI = IRI.create("http://example.org/phyloref1");
      OWLNamedIndividual phyloref1 = df.getOWLNamedIndividual(phyloref1IRI);

      // Mark it as a phyloreference.
      // (Eventually we would like to test whether we can infer that an
      // individual belongs to class phyloref:Phyloreference based on its
      // properties alone, but so far no property does this.)
      axioms.add(
          df.getOWLClassAssertionAxiom(
              df.getOWLClass(
                  IRI.create("http://ontology.phyloref.org/phyloref.owl#Phyloreference")),
              phyloref1));

      // Give the phyloreference a label.
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              df.getRDFSLabel(), phyloref1IRI, df.getOWLLiteral("Test phyloreference", "en")));

      // TODO: Set up annotation properties for setting statuses.

      // Set up the test ontology.
      testOntology = ontologyManager.createOntology(new HashSet<>(axioms));
    }

    @Test
    @DisplayName("can retrieve lists of phylorefs without reasoning")
    void canRetrievePhylorefsWithoutReasoning() {
      OWLDataFactory df = ontologyManager.getOWLDataFactory();
      OWLNamedIndividual phyloref =
          df.getOWLNamedIndividual(IRI.create("http://example.org/phyloref1"));

      Set<OWLNamedIndividual> phylorefs =
          PhylorefHelper.getPhyloreferencesWithoutReasoning(testOntology);
      assertEquals(1, phylorefs.size());
      assertTrue(
          phylorefs.contains(phyloref),
          "Phyloref 'phyloref1' has been retrieved without reasoning");
    }

    @Test
    @DisplayName("can retrieve lists of phylorefs with reasoning")
    void canRetrievePhylorefsWithReasoning() {
      OWLDataFactory df = ontologyManager.getOWLDataFactory();
      OWLReasoner reasoner = new JFactFactory().createNonBufferingReasoner(testOntology);
      OWLNamedIndividual phyloref =
          df.getOWLNamedIndividual(IRI.create("http://example.org/phyloref1"));

      // Note that this should be identical to the "without reasoning" code in
      // the absence of individuals that are implied to be Phyloreferences but
      // not explicitly stated to be phylorefs. This is not tested yet!

      Set<OWLNamedIndividual> phylorefs = PhylorefHelper.getPhyloreferences(testOntology, reasoner);
      assertEquals(1, phylorefs.size());
      assertTrue(
          phylorefs.contains(phyloref), "Phyloref 'phyloref1' has been retrieved with reasoning");
    }
  }
}
