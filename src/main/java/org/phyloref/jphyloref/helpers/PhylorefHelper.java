/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
    public static final IRI IRI_PHYLOREFERENCE = IRI.create("http://phyloinformatics.net/phyloref.owl#Phyloreference");
    // IRIs used in this command
    public static IRI iri_class_Phyloreference = IRI.create("http://phyloinformatics.net/phyloref.owl#Phyloreference");
    public static IRI iri_has_specifier = IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#has_specifier");
    public static IRI IRI_PHYLOGENY_CONTAINING_NODE = IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#in_phylogeny");
    public static IRI IRI_NAME_OF_EXPECTED_PHYLOREF = IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#expected_phyloreference_named");
    
    public static Set<OWLNamedIndividual> getPhyloreferences(OWLOntology ontology, OWLReasoner reasoner) {
        // Get a list of all phyloreferences. First, we need to know what a Phyloreference is.
        Set<OWLEntity> set_phyloref_Phyloreference = ontology.getEntitiesInSignature(IRI_PHYLOREFERENCE);
        if (set_phyloref_Phyloreference.isEmpty()) {
            throw new RuntimeException("Class 'phyloref:Phyloreference' is not defined in ontology.");
        }
        if (set_phyloref_Phyloreference.size() > 1) {
            throw new RuntimeException("Class 'phyloref:Phyloreference' is defined multiple times in ontology.");
        }

        OWLEntity phyloref_Phyloreference = set_phyloref_Phyloreference.iterator().next();
        return reasoner.getInstances(phyloref_Phyloreference.asOWLClass(), true).getFlattened();
    }
    
}
