package org.phyloref.jphyloref.helpers;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * A Phyloreference helper class. It consists of common terms and helper functions to make writing
 * about Phyloreferences easier.
 *
 * <p>Eventually, this will be reorganized into a Phyloreferencing Java library, but we don't need
 * that level of sophistication just yet.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class PhylorefHelper {
  // IRIs used in this package.

  /** IRI for OWL class Phylogeny */
  public static final IRI IRI_CDAO_NODE = IRI.create("http://purl.obolibrary.org/obo/CDAO_0000140");

  /** IRI for OWL class Phyloreference */
  public static final IRI IRI_PHYLOREFERENCE =
      IRI.create("http://ontology.phyloref.org/phyloref.owl#Phyloreference");

  /** IRI for the OWL object property indicating which phylogeny a node belongs to */
  public static final IRI IRI_PHYLOGENY_CONTAINING_NODE =
      IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#in_phylogeny");

  /** IRI for the OWL data property indicating the label of the expected phyloreference */
  public static final IRI IRI_NAME_OF_EXPECTED_PHYLOREF =
      IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#expected_phyloreference_named");

  /** IRI for the OWL object property indicating which specifiers had not been matched */
  public static final IRI IRI_PHYLOREF_UNMATCHED_SPECIFIER =
      IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#has_unmatched_specifier");

  /** IRI for the OWL data property with the verbatim clade definition */
  public static final IRI IRI_CLADE_DEFINITION =
      IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#clade_definition");

  /**
   * IRI for the OWL object property that associates a publication with its publication status at a
   * particular time.
   */
  public static final IRI IRI_PSO_HOLDS_STATUS_IN_TIME =
      IRI.create("http://purl.org/spar/pso/holdsStatusInTime");

  /**
   * IRI for the OWL object property that indicates the publication status of a publication status
   * at a particular time.
   */
  public static final IRI IRI_PSO_WITH_STATUS = IRI.create("http://purl.org/spar/pso/withStatus");

  /**
   * IRI for the OWL object property that associates a publication status at a particular time with
   * a particular time.
   */
  public static final IRI IRI_TVC_AT_TIME =
      IRI.create("http://www.essepuntato.it/2012/04/tvc/atTime");

  /** IRI for the OWL data property that indicates when a publication status time begins */
  public static final IRI IRI_TIMEINT_HAS_INTERVAL_START_DATE =
      IRI.create(
          "http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalStartDate");

  /** IRI for the OWL data property that indicates when a publication status time ends */
  public static final IRI IRI_TIMEINT_HAS_INTERVAL_END_DATE =
      IRI.create(
          "http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalEndDate");

  /** IRI for the publication status of "Draft" */
  public static final IRI IRI_PSO_DRAFT = IRI.create("http://purl.org/spar/pso/draft");

  /** IRI for the publication status of "Submitted" */
  public static final IRI IRI_PSO_SUBMITTED = IRI.create("http://purl.org/spar/pso/submitted");

  /** IRI for the publication status of "Published" */
  public static final IRI IRI_PSO_PUBLISHED = IRI.create("http://purl.org/spar/pso/published");

  /**
   * Get a list of phyloreferences in this ontology without reasoning. This method does not use the
   * reasoner, and so will only find individuals asserted to have a type of phyloref:Phyloreference.
   */
  public static Set<OWLNamedIndividual> getPhyloreferencesWithoutReasoning(OWLOntology ontology) {
    // Get a list of all phyloreferences. First, we need to know what a Phyloreference is.
    Set<OWLEntity> set_phyloref_Phyloreference =
        ontology.getEntitiesInSignature(IRI_PHYLOREFERENCE);
    if (set_phyloref_Phyloreference.isEmpty()) {
      throw new RuntimeException("Class 'phyloref:Phyloreference' is not defined in ontology.");
    }
    if (set_phyloref_Phyloreference.size() > 1) {
      throw new RuntimeException(
          "Class 'phyloref:Phyloreference' is defined multiple times in ontology.");
    }

    OWLClass phyloref_Phyloreference = set_phyloref_Phyloreference.iterator().next().asOWLClass();
    Set<OWLNamedIndividual> phylorefs = new HashSet<>();
    Set<OWLClassAssertionAxiom> classAssertions = ontology.getAxioms(AxiomType.CLASS_ASSERTION);

    for (OWLClassAssertionAxiom classAssertion : classAssertions) {
      // Does this assertion involve class:Phyloreference and a named individual?
      if (classAssertion.getIndividual().isNamed()
          && classAssertion.getClassesInSignature().contains(phyloref_Phyloreference)) {
        // If so, then the individual is a phyloreferences!
        phylorefs.add(classAssertion.getIndividual().asOWLNamedIndividual());
      }
    }

    return phylorefs;
  }

  /**
   * Get a list of phyloreferences in this ontology. This method uses the reasoner, and so will find
   * all individuals determined to be of type phyloref:Phyloreference.
   *
   * @param ontology The OWL Ontology within with we should look for phylorefs
   * @param reasoner The reasoner to use. May be null.
   */
  public static Set<OWLNamedIndividual> getPhyloreferences(
      OWLOntology ontology, OWLReasoner reasoner) {
    // If no reasoner is provided, we can't find all individuals that are
    // inferred to be phyloref:Phyloreference, but we can still find all
    // individuals that are stated to be phyloref:Phyloreference. So let's do that!
    if (reasoner == null) return PhylorefHelper.getPhyloreferencesWithoutReasoning(ontology);

    // Get a list of all phyloreferences. First, we need to know what a Phyloreference is.
    Set<OWLEntity> set_phyloref_Phyloreference =
        ontology.getEntitiesInSignature(IRI_PHYLOREFERENCE);
    if (set_phyloref_Phyloreference.isEmpty()) {
      throw new IllegalArgumentException(
          "Class " + IRI_PHYLOREFERENCE + " is not defined in ontology.");
    }
    if (set_phyloref_Phyloreference.size() > 1) {
      throw new IllegalArgumentException(
          "Class " + IRI_PHYLOREFERENCE + " is defined multiple times in ontology.");
    }

    OWLClass phyloref_Phyloreference = set_phyloref_Phyloreference.iterator().next().asOWLClass();
    return reasoner.getInstances(phyloref_Phyloreference, true).getFlattened();
  }

  /** A wrapper for a phyloref status at a particular point in time. */
  public static class PhylorefStatus {
    private OWLNamedIndividual phyloref;
    private IRI statusIRI;
    private Instant intervalStart;
    private Instant intervalEnd;

    /**
     * Create a PhylorefStatus. All arguments except status are optional, and may be null.
     *
     * @param phyloref The phyloreference containing this status.
     * @param status The status of this phyloreference, as an individual in the class
     *     http://purl.org/spar/pso/PublicationStatus. Required.
     * @param intervalStart The interval at which this status starts.
     * @param intervalEnd The interval at which this status ends. May be null if this status hasn't
     *     ended yet.
     */
    public PhylorefStatus(
        OWLNamedIndividual phyloref, IRI status, Instant intervalStart, Instant intervalEnd) {
      this.phyloref = phyloref;
      this.statusIRI = status;
      this.intervalStart = intervalStart;
      this.intervalEnd = intervalEnd;

      if (status == null)
        throw new IllegalArgumentException(
            "No status provided to PhylorefStatus, which is a required argument");
    }

    /** @return the phyloreference this status is associated with. May be null. */
    public OWLNamedIndividual getPhyloref() {
      return phyloref;
    }

    /**
     * @return the status of this phyloreference, as an individual in the class
     *     http://purl.org/spar/pso/PublicationStatus
     */
    public IRI getStatus() {
      return statusIRI;
    }

    /** @return the interval at which this status starts. */
    public Instant getIntervalStart() {
      return intervalStart;
    }

    /**
     * @return the interval at which this status ends. May be null if this status hasn't ended yet.
     */
    public Instant getIntervalEnd() {
      return intervalEnd;
    }

    /** @return a String representation of this phyloref status */
    @Override
    public String toString() {
      StringBuffer statusString = new StringBuffer("phyloreference status " + statusIRI);

      if (getIntervalStart() != null)
        statusString.append(" starting at " + getIntervalStart().toString());
      if (getIntervalEnd() != null)
        statusString.append(" ending at " + getIntervalEnd().toString());

      return statusString.toString();
    }
  }

  /**
   * Return a list of PhylorefStatuses associated with a particular phyloreference in the provided
   * ontology.
   *
   * @param phyloref The phyloreference whose statuses are being queried.
   * @param ontology The ontology within which this phyloreference is defined.
   * @return A list of phyloref statuses.
   */
  public static List<PhylorefStatus> getStatusesForPhyloref(
      OWLNamedIndividual phyloref, OWLOntology ontology) {
    List<PhylorefStatus> statuses = new ArrayList<>();

    // Set up the OWL annotation properties we need to look up the phyloref statuses.
    OWLDataFactory dataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();
    OWLClass phylorefAsClass = dataFactory.getOWLClass(phyloref.getIRI());

    OWLAnnotationProperty pso_holdsStatusInTime =
        dataFactory.getOWLAnnotationProperty(PhylorefHelper.IRI_PSO_HOLDS_STATUS_IN_TIME);
    OWLAnnotationProperty pso_withStatus =
        dataFactory.getOWLAnnotationProperty(PhylorefHelper.IRI_PSO_WITH_STATUS);
    OWLAnnotationProperty tvc_atTime =
        dataFactory.getOWLAnnotationProperty(PhylorefHelper.IRI_TVC_AT_TIME);
    OWLAnnotationProperty timeinterval_hasIntervalStartDate =
        dataFactory.getOWLAnnotationProperty(PhylorefHelper.IRI_TIMEINT_HAS_INTERVAL_START_DATE);
    OWLAnnotationProperty timeinterval_hasIntervalEndDate =
        dataFactory.getOWLAnnotationProperty(PhylorefHelper.IRI_TIMEINT_HAS_INTERVAL_END_DATE);

    // Retrieve holdsStatusInTime to determine the active status of this phyloreference.
    Collection<OWLAnnotation> holdsStatusInTime =
        EntitySearcher.getAnnotations(phylorefAsClass, ontology, pso_holdsStatusInTime);

    // Read through the list of OWLAnnotations to create corresponding PhylorefStatus objects.
    for (OWLAnnotation statusInTime : holdsStatusInTime) {
      // Each statusInTime entry should have one status (pso:withStatus)
      // and a number of time intervals (tvc:atTime). We collect all
      // statusues and test to see if any of those time intervals are
      // "incomplete", i.e. they have a start date but no end date.
      IRI phylorefStatusIRI = null;
      Instant intervalStartDate = null;
      Instant intervalEndDate = null;

      for (OWLAnonymousIndividual indiv_statusInTime : statusInTime.getAnonymousIndividuals()) {
        for (OWLAnnotationAssertionAxiom axiom :
            ontology.getAnnotationAssertionAxioms(indiv_statusInTime)) {
          if (axiom.getProperty().equals(tvc_atTime)) {
            for (OWLAnonymousIndividual indiv_atTime : axiom.getValue().getAnonymousIndividuals()) {
              for (OWLAnnotationAssertionAxiom axiom_interval :
                  ontology.getAnnotationAssertionAxioms(indiv_atTime)) {
                // Look for timeinterval:hasIntervalStartDate and timeinterval:hasIntervalEndDate
                // data properties.
                if (axiom_interval.getProperty().equals(timeinterval_hasIntervalStartDate)) {
                  try {
                    intervalStartDate =
                        ZonedDateTime.parse(
                                axiom_interval.getValue().asLiteral().get().getLiteral())
                            .toInstant();
                  } catch (DateTimeParseException ex) {
                    // If we have a start date but can't parse it, record it as the earliest
                    // possible time.
                    intervalStartDate = Instant.MIN;
                  }
                }
                if (axiom_interval.getProperty().equals(timeinterval_hasIntervalEndDate)) {
                  try {
                    intervalEndDate =
                        ZonedDateTime.parse(
                                axiom_interval.getValue().asLiteral().get().getLiteral())
                            .toInstant();
                  } catch (DateTimeParseException ex) {
                    // If we have an end date but can't parse it, record it at the latest possible
                    // time.
                    intervalEndDate = Instant.MAX;
                  }
                }
              }
            }
          } else if (axiom.getProperty().equals(pso_withStatus)) {
            phylorefStatusIRI = (IRI) axiom.getValue();
          } else {
            throw new IllegalArgumentException(
                "Phyloreference " + phyloref + " contains an unknown axiom: " + axiom);
          }
        }
      }

      statuses.add(
          new PhylorefHelper.PhylorefStatus(
              phyloref, phylorefStatusIRI, intervalStartDate, intervalEndDate));
    }

    return statuses;
  }
}
