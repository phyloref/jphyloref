package org.phyloref.jphyloref.helpers;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * A Phyloreference helper class. It consists of common terms and helper
 * functions to make writing about Phyloreferences easier.
 *
 * Eventually, this will be reorganized into a Phyloreferencing Java library,
 * but we don't need that level of sophistication just yet.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class PhylorefHelper {
    // IRIs used in this package.

    /** IRI for OWL class Phyloreference */
    public static final IRI IRI_PHYLOREFERENCE = IRI.create("http://phyloinformatics.net/phyloref.owl#Phyloreference");

    /** IRI for the OWL object property indicating which phylogeny a node belongs to */
    public static final IRI IRI_PHYLOGENY_CONTAINING_NODE = IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#in_phylogeny");

    /** IRI for the OWL data property indicating the label of the expected phyloreference */
    public static final IRI IRI_NAME_OF_EXPECTED_PHYLOREF = IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#expected_phyloreference_named");

    /** IRI for the OWL object property indicating which specifiers had not been matched */
    public static final IRI IRI_PHYLOREF_UNMATCHED_SPECIFIER = IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#has_unmatched_specifier");

    /** IRI for the OWL data property with the verbatim clade definition */
    public static final IRI IRI_CLADE_DEFINITION = IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#clade_definition");

    /**
     * Get a list of phyloreferences in this ontology without reasoning. This method does not use
     * the reasoner, and so will only find individuals asserted to have a type
     * of phyloref:Phyloreference.
     */
    public static Set<OWLNamedIndividual> getPhyloreferencesWithoutReasoning(OWLOntology ontology) {
        // Get a list of all phyloreferences. First, we need to know what a Phyloreference is.
        Set<OWLEntity> set_phyloref_Phyloreference = ontology.getEntitiesInSignature(IRI_PHYLOREFERENCE);
        if (set_phyloref_Phyloreference.isEmpty()) {
            throw new RuntimeException("Class 'phyloref:Phyloreference' is not defined in ontology.");
        }
        if (set_phyloref_Phyloreference.size() > 1) {
            throw new RuntimeException("Class 'phyloref:Phyloreference' is defined multiple times in ontology.");
        }

        OWLClass phyloref_Phyloreference = set_phyloref_Phyloreference.iterator().next().asOWLClass();
        Set<OWLNamedIndividual> phylorefs = new HashSet<>();
        Set<OWLClassAssertionAxiom> classAssertions = ontology.getAxioms(AxiomType.CLASS_ASSERTION);

        for(OWLClassAssertionAxiom classAssertion: classAssertions) {
            // Does this assertion involve class:Phyloreference and a named individual?
            if(
                classAssertion.getIndividual().isNamed() &&
                classAssertion.getClassesInSignature().contains(phyloref_Phyloreference)
            ) {
                // If so, then the individual is a phyloreferences!
                phylorefs.add(classAssertion.getIndividual().asOWLNamedIndividual());
            }
        }

        return phylorefs;
    }

    /**
     * Get a list of phyloreferences in this ontology. This method uses the
     * reasoner, and so will find all individuals determined to be of
     * type phyloref:Phyloreference.
     * 
     * @param ontology The OWL Ontology within with we should look for phylorefs
     * @param reasoner The reasoner to use. May be null.
     */
    public static Set<OWLNamedIndividual> getPhyloreferences(OWLOntology ontology, OWLReasoner reasoner) {
        // If no reasoner is provided, we can't find all individuals that are
        // inferred to be phyloref:Phyloreference, but we can still find all
        // individuals that are stated to be phyloref:Phyloreference. So let's do that!
        if(reasoner == null) return PhylorefHelper.getPhyloreferencesWithoutReasoning(ontology);
        
        // Get a list of all phyloreferences. First, we need to know what a Phyloreference is.
        Set<OWLEntity> set_phyloref_Phyloreference = ontology.getEntitiesInSignature(IRI_PHYLOREFERENCE);
        if (set_phyloref_Phyloreference.isEmpty()) {
            throw new IllegalArgumentException("Class " + IRI_PHYLOREFERENCE + " is not defined in ontology.");
        }
        if (set_phyloref_Phyloreference.size() > 1) {
            throw new IllegalArgumentException("Class " + IRI_PHYLOREFERENCE + " is defined multiple times in ontology.");
        }

        OWLClass phyloref_Phyloreference = set_phyloref_Phyloreference.iterator().next().asOWLClass();
        return reasoner.getInstances(phyloref_Phyloreference, true).getFlattened();
    }
}