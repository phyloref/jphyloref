package org.phyloref.jphyloref.helpers;

import java.util.Set;
import org.semanticweb.owlapi.model.IRI;
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
     * Extract a list of phyloreferences, i.e. instances of IRI_PHYLOREFERENCE that are punned as OWL classes.
     *  
     * @param ontology The ontology to extract the phyloreferences from
     * @param reasoner A reasoner to use in extracting the phyloreferences
     * @return A Set of OWLNamedIndividuals corresponding to phyloreferences in the specified ontology
     */
    public static Set<OWLNamedIndividual> getPhyloreferences(OWLOntology ontology, OWLReasoner reasoner) {
        // Get a list of all phyloreferences. First, we need to know what a Phyloreference is.
        Set<OWLEntity> set_phyloref_Phyloreference = ontology.getEntitiesInSignature(IRI_PHYLOREFERENCE);
        if (set_phyloref_Phyloreference.isEmpty()) {
            throw new IllegalArgumentException("Class " + IRI_PHYLOREFERENCE + " is not defined in ontology.");
        }
        if (set_phyloref_Phyloreference.size() > 1) {
            throw new IllegalArgumentException("Class " + IRI_PHYLOREFERENCE + " is defined multiple times in ontology.");
        }

        // Get all instances of IRI_PHYLOREFERENCE.
        OWLEntity phyloref_Phyloreference = set_phyloref_Phyloreference.iterator().next();
        return reasoner.getInstances(phyloref_Phyloreference.asOWLClass(), true).getFlattened();
    }
    
}
