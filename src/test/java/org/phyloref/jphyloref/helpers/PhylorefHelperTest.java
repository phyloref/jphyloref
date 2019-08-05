package org.phyloref.jphyloref.helpers;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.phyloref.jphyloref.helpers.PhylorefHelper.PhylorefStatus;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
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
      OWLClass phyloref1 = df.getOWLClass(phyloref1IRI);

      // Set it as a subclass of phyloref:Phyloreference.
      axioms.add(
          df.getOWLSubClassOfAxiom(
              phyloref1,
              df.getOWLClass(
                  IRI.create("http://ontology.phyloref.org/phyloref.owl#Phyloreference"))));

      // Give the phyloreference a label.
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              df.getRDFSLabel(), phyloref1IRI, df.getOWLLiteral("Test phyloreference", "en")));

      // Set up the test ontology.
      testOntology = ontologyManager.createOntology(new HashSet<>(axioms));
    }

    @Test
    @DisplayName("can retrieve lists of phylorefs without reasoning")
    void canRetrievePhylorefsWithoutReasoning() {
      OWLDataFactory df = ontologyManager.getOWLDataFactory();
      OWLClass phyloref = df.getOWLClass(IRI.create("http://example.org/phyloref1"));

      Set<OWLClass> phylorefs = PhylorefHelper.getPhyloreferencesWithoutReasoning(testOntology);
      assertEquals(1, phylorefs.size());
      assertTrue(
          phylorefs.contains(phyloref),
          "Phyloref 'phyloref1' has been retrieved without reasoning");

      // Calling getPhyloreferences() with a null reasoner should also return
      // the same results.
      phylorefs = PhylorefHelper.getPhyloreferences(testOntology, null);
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
      OWLClass phyloref = df.getOWLClass(IRI.create("http://example.org/phyloref1"));

      // Note that this should be identical to the "without reasoning" code in
      // the absence of individuals that are implied to be Phyloreferences but
      // not explicitly stated to be phylorefs. This is not tested yet!

      Set<OWLClass> phylorefs = PhylorefHelper.getPhyloreferences(testOntology, reasoner);
      assertEquals(1, phylorefs.size());
      assertTrue(
          phylorefs.contains(phyloref), "Phyloref 'phyloref1' has been retrieved with reasoning");
    }
  }

  @Nested
  @DisplayName("has methods for retrieving nodes in phylorefs that can")
  class ResolvedNodesRetrievalTest {
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
      OWLClass phyloref1 = df.getOWLClass(phyloref1IRI);

      // Set it as a subclass of phyloref:Phyloreference.
      axioms.add(
          df.getOWLSubClassOfAxiom(
              phyloref1,
              df.getOWLClass(
                  IRI.create("http://ontology.phyloref.org/phyloref.owl#Phyloreference"))));

      // Create a second phyloreference that is a subclass of the first.
      IRI phyloref2IRI = IRI.create("http://example.org/phyloref2");
      OWLClass phyloref2 = df.getOWLClass(phyloref2IRI);
      axioms.add(df.getOWLSubClassOfAxiom(phyloref2, phyloref1));

      // Create two nodes, one an instance of phyloref1, and the other an instance of phyloref2.
      IRI node1IRI = IRI.create("http://example.org/node1");
      OWLNamedIndividual node1 = df.getOWLNamedIndividual(node1IRI);
      axioms.add(df.getOWLClassAssertionAxiom(phyloref1, node1));

      IRI node2IRI = IRI.create("http://example.org/node2");
      OWLNamedIndividual node2 = df.getOWLNamedIndividual(node2IRI);
      axioms.add(df.getOWLClassAssertionAxiom(phyloref2, node2));

      // Set up the test ontology.
      testOntology = ontologyManager.createOntology(new HashSet<>(axioms));
    }

    @Test
    @DisplayName("can retrieve nodes without reasoning")
    void canRetrieveNodesWithoutReasoning() {
      OWLDataFactory df = ontologyManager.getOWLDataFactory();
      OWLClass phyloref1 = df.getOWLClass(IRI.create("http://example.org/phyloref1"));

      Set<OWLClass> phylorefs = PhylorefHelper.getPhyloreferencesWithoutReasoning(testOntology);
      assertEquals(1, phylorefs.size());
      assertTrue(
          phylorefs.contains(phyloref1),
          "Phyloref 'phyloref1' has been retrieved without reasoning");

      OWLNamedIndividual node1 = df.getOWLNamedIndividual(IRI.create("http://example.org/node1"));
      Set<OWLNamedIndividual> nodes = PhylorefHelper.getNodesInClass(phyloref1, testOntology, null);
      assertEquals(1, nodes.size());
      assertTrue(
          nodes.contains(node1),
          "Phyloref 'phyloref1' contains 'node1' as expected without reasoning");
    }

    @Test
    @DisplayName("can retrieve nodes with reasoning")
    void canRetrieveNodesWithReasoning() {
      OWLDataFactory df = ontologyManager.getOWLDataFactory();
      OWLReasoner reasoner = new JFactFactory().createNonBufferingReasoner(testOntology);
      OWLClass phyloref1 = df.getOWLClass(IRI.create("http://example.org/phyloref1"));
      OWLClass phyloref2 = df.getOWLClass(IRI.create("http://example.org/phyloref2"));

      Set<OWLClass> phylorefs = PhylorefHelper.getPhyloreferences(testOntology, reasoner);
      assertEquals(2, phylorefs.size());
      assertTrue(
          phylorefs.contains(phyloref1), "Phyloref 'phyloref1' has been retrieved with reasoning");
      assertTrue(
          phylorefs.contains(phyloref2), "Phyloref 'phyloref2' has been retrieved with reasoning");

      OWLNamedIndividual node1 = df.getOWLNamedIndividual(IRI.create("http://example.org/node1"));
      OWLNamedIndividual node2 = df.getOWLNamedIndividual(IRI.create("http://example.org/node2"));
      Set<OWLNamedIndividual> nodes =
          PhylorefHelper.getNodesInClass(phyloref1, testOntology, reasoner);
      assertEquals(2, nodes.size());
      assertTrue(
          nodes.contains(node1),
          "Phyloref 'phyloref1' contains 'node1' as expected with reasoning");
      assertTrue(
          nodes.contains(node2),
          "Phyloref 'phyloref1' contains 'node2' as expected with reasoning");
    }
  }

  @Nested
  @DisplayName("has a class for storing phyloref statuses that")
  class PhylorefStatusTest {
    @Test
    @DisplayName("fails unless a status has been provided")
    void checkPhylorefStatusConstructor() {
      PhylorefStatus status1 = new PhylorefStatus(null, IRI.create("http://"), null, null);
      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class, () -> new PhylorefStatus(null, null, null, null));
      assertEquals(
          "No status provided to PhylorefStatus, which is a required argument", ex.getMessage());
    }
  }

  @Nested
  @DisplayName("has methods for retrieving phyloref statuses that")
  class PhylorefStatusRetrievalTest {
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
      OWLClass phyloref1 = df.getOWLClass(phyloref1IRI);

      // Set up annotation properties for setting statuses.
      axioms.addAll(
          createStatus(
              df,
              phyloref1IRI,
              IRI.create("http://purl.org/spar/pso/draft"),
              Instant.parse("2018-11-14T01:00:00.00Z"),
              Instant.parse("2018-11-14T02:00:00.00Z")));

      axioms.addAll(
          createStatus(
              df,
              phyloref1IRI,
              IRI.create("http://purl.org/spar/pso/final-draft"),
              Instant.parse("2018-11-14T02:00:00.00Z"),
              Instant.parse("2018-11-14T03:00:00.00Z")));

      axioms.addAll(
          createStatus(
              df,
              phyloref1IRI,
              IRI.create("http://purl.org/spar/pso/submitted"),
              Instant.parse("2018-11-14T03:00:00.00Z"),
              Instant.parse("2018-11-14T04:00:00.00Z")));

      axioms.addAll(
          createStatus(
              df,
              phyloref1IRI,
              IRI.create("http://purl.org/spar/pso/published"),
              Instant.parse("2018-11-14T04:00:00.00Z"),
              null));

      // Set up the test ontology.
      testOntology = ontologyManager.createOntology(new HashSet<>(axioms));
    }

    /**
     * Return an OWLAnonymousIndividual (i.e. an OWLAnnotationObject) that can be the target of a
     * `pso:withStatus`.
     */
    private List<OWLAxiom> createStatus(
        OWLDataFactory df, IRI phylorefIRI, IRI status, Instant startTime, Instant endTime) {
      // Create list of axioms.
      List<OWLAxiom> axioms = new ArrayList<>();

      // Add start time interval to a timeInterval anonymous individual.
      OWLAnonymousIndividual timeInterval = df.getOWLAnonymousIndividual();
      if (startTime != null) {
        axioms.add(
            df.getOWLAnnotationAssertionAxiom(
                df.getOWLAnnotationProperty(
                    IRI.create(
                        "http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalStartDate")),
                timeInterval,
                df.getOWLLiteral(startTime.toString(), OWL2Datatype.XSD_DATE_TIME)));
      }

      // Add end time interval to a timeInterval anonymous individual.
      if (endTime != null) {
        axioms.add(
            df.getOWLAnnotationAssertionAxiom(
                df.getOWLAnnotationProperty(
                    IRI.create(
                        "http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalEndDate")),
                timeInterval,
                df.getOWLLiteral(endTime.toString(), OWL2Datatype.XSD_DATE_TIME)));
      }

      // Add timeInterval to a holdsStatusInTime anonymous individual.
      OWLAnonymousIndividual holdsStatusInTime = df.getOWLAnonymousIndividual();
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              df.getOWLAnnotationProperty(
                  IRI.create("http://www.essepuntato.it/2012/04/tvc/atTime")),
              holdsStatusInTime,
              timeInterval));

      // Add the status to a holdsStatusInTime anonymous individual.
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              df.getOWLAnnotationProperty(IRI.create("http://purl.org/spar/pso/withStatus")),
              holdsStatusInTime,
              status));

      // Annotate the phyloref with the holdsStatusInTime anonymous individual.
      axioms.add(
          df.getOWLAnnotationAssertionAxiom(
              df.getOWLAnnotationProperty(IRI.create("http://purl.org/spar/pso/holdsStatusInTime")),
              phylorefIRI,
              holdsStatusInTime));

      // Return the axioms that need to be added.
      return axioms;
    }

    @Test
    @DisplayName("can retrieve current statuses for a particular phyloreference")
    void canRetrieveStatuses() throws OWLOntologyStorageException {
      OWLDataFactory df = ontologyManager.getOWLDataFactory();
      OWLClass phyloref = df.getOWLClass(IRI.create("http://example.org/phyloref1"));

      // Uncomment the next line to provide a copy of the test ontology for debugging.
      // testOntology.saveOntology(new FileDocumentTarget(new File("./output.txt")));

      List<PhylorefStatus> statuses = PhylorefHelper.getStatusesForPhyloref(phyloref, testOntology);
      assertEquals(4, statuses.size());

      // Note that OWL doesn't really have a concept of a list of statuses;
      // we have no guarantee as to which order these statuses will appear in.

      // Find the first status somewhere in this list.
      PhylorefStatus first =
          statuses
              .stream()
              .filter(st -> st.getStatus().equals(IRI.create("http://purl.org/spar/pso/draft")))
              .findAny()
              .get();

      assertEquals(phyloref, first.getPhyloref());
      assertEquals(Instant.parse("2018-11-14T01:00:00.00Z"), first.getIntervalStart());
      assertEquals(Instant.parse("2018-11-14T02:00:00.00Z"), first.getIntervalEnd());
      assertEquals(
          "phyloreference status http://purl.org/spar/pso/draft starting at 2018-11-14T01:00:00Z ending at 2018-11-14T02:00:00Z",
          first.toString());

      // Find the final status somewhere in this list.
      PhylorefStatus last =
          statuses
              .stream()
              .filter(st -> st.getStatus().equals(IRI.create("http://purl.org/spar/pso/published")))
              .findAny()
              .get();

      assertEquals(phyloref, last.getPhyloref());
      assertEquals(Instant.parse("2018-11-14T04:00:00.00Z"), last.getIntervalStart());
      assertNull(last.getIntervalEnd());
      assertEquals(
          "phyloreference status http://purl.org/spar/pso/published starting at 2018-11-14T04:00:00Z",
          last.toString());

      // How many are "current" (i.e. missing an end time)?
      assertEquals(1, statuses.stream().filter(st -> st.getIntervalEnd() == null).count());
    }
  }
}
