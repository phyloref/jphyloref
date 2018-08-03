package org.phyloref.jphyloref.helpers;

import java.util.HashMap;
import java.util.Map;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.Version;

import uk.ac.manchester.cs.jfact.JFactFactory;

/**
 * The ReasonerHelper provides methods to help create and manage
 * OWL Reasoners, and to allow the user to choose a different
 * reasoner to carry out any specified task. 
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class ReasonerHelper {
	/** A map of reasoner names and the corresponding reasoner factory */
	private static Map<String, OWLReasonerFactory> reasonerFactories = new HashMap<>();
	
	static {
		/*
		 * Set up a list of reasoner names and their corresponding reasoner factory.
		 */
		reasonerFactories.put("jfact", new JFactFactory());
	}

	/**
	 * Get reasoner factory by name.
	 */
	public OWLReasonerFactory getReasonerFactory(String name) {
		// Look it up.
		if(reasonerFactories.containsKey(name)) {
			return reasonerFactories.get(name);
		}
		
		// If all else fails, we default to JFact.
		return new JFactFactory();
	}
	
	/**
	 * Get all reasoner factories.
	 */
	public static Map<String, OWLReasonerFactory> getReasonerFactories() {
		return reasonerFactories;
	}
	
	/**
	 * Get the Version of a particular reasoner factory. Unfortunately, the only way to
	 * determine this is to create an OWLReasoner, but we can cache that. 
	 */
	public static Version getReasonerFactoryVersion(OWLReasonerFactory factory) {
		try {
			OWLReasoner reasoner = factory.createNonBufferingReasoner(OWLManager.createOWLOntologyManager().createOntology());
			return reasoner.getReasonerVersion();
		} catch (OWLOntologyCreationException e) {
			return new Version(0, 0, 0, 0);
		}
	}
	
	/**
	 * Get the version of a particular reasoner factory as a String.
	 */
	public static String getReasonerFactoryVersionString(OWLReasonerFactory factory) {
		Version version = getReasonerFactoryVersion(factory);
	
		return version.getMajor() + "." + version.getMinor() + "." + version.getBuild() + "." + version.getPatch();
	}
}
