package org.phyloref.jphyloref.commands;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.phyloref.jphyloref.helpers.OWLHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.tap4j.model.Comment;
import org.tap4j.model.Plan;
import org.tap4j.model.TestResult;
import org.tap4j.model.TestSet;
import org.tap4j.producer.TapProducer;
import org.tap4j.producer.TapProducerFactory;
import org.tap4j.util.StatusValues;

import uk.ac.manchester.cs.jfact.JFactReasoner;
import uk.ac.manchester.cs.jfact.kernel.options.JFactReasonerConfiguration;

/**
 * Test whether the phyloreferences in the provided ontology resolve correctly.
 * 
 * At the moment, this works on OWL ontologies, but there's really no reason
 * we couldn't test the labeled.json file directly! Maybe at a later date?
 * 
 * I can't resist using the Test Anything Protocol here, which has nice libraries
 * in both Python and Java.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 *
 */
public class TestCommand implements Command {
	public String getName() { return "test"; }
	public String getDescription() { return "Test the phyloreferences in the provided ontology to determine if they resolved correctly."; }
	
	public void addCommandLineOptions(Options opts) {
		opts.addOption("i", "input", true, "The input ontology to read in RDF/XML (can also be provided without the '-i')");
	}
	
	/**
	 * Execute this command with the provided command line options. 
	 */
	public void execute(CommandLine cmdLine) throws RuntimeException {
		String str_input = cmdLine.getOptionValue("input");
		
		if(str_input == null && cmdLine.getArgList().size() > 1) {
			// No 'input'? Maybe it's just provided as a left-over option?
			str_input = cmdLine.getArgList().get(1); 
		}
		
		if(str_input == null) {
			throw new RuntimeException("Error: no input ontology specified (use '-i input.owl')");
		}
		
		File inputFile = new File(str_input);
		if(!inputFile.exists() || !inputFile.canRead()) {
			throw new RuntimeException("Error: cannot read from input ontology '" + str_input + "'");
		}
		
		System.err.println("Input: " + str_input);
		
		// Load the ontology into inputOntology.
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology;
		try {
			ontology = manager.loadOntologyFromOntologyDocument(inputFile);
		} catch (OWLOntologyCreationException ex) {
			throw new RuntimeException("Could not load ontology '" + inputFile + "': " + ex);
		}
		
		// Ontology loaded.
		System.err.println("Loaded ontology: " + ontology);
		
		// Now to reason.
		JFactReasonerConfiguration config = new JFactReasonerConfiguration();
		JFactReasoner reasoner = new JFactReasoner(ontology, config, BufferingMode.BUFFERING);
		
		//reasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
		// System.err.println("Precomputing complete.");

		// Get a list of all phyloreferences. First, we need to know what a Phyloreference is.
		IRI iri_class_Phyloreference = IRI.create("http://phyloinformatics.net/phyloref.owl#Phyloreference");
		Set<OWLEntity> optClassPhyloreference = ontology.getEntitiesInSignature(iri_class_Phyloreference);
		if(optClassPhyloreference.isEmpty()) {
			throw new RuntimeException("Class 'phyloref:Phyloreference' is not defined in ontology.");
		}
		if(optClassPhyloreference.size() > 1) {
			throw new RuntimeException("Class 'phyloref:Phyloreference' is defined multiple times in ontology.");
		}
		
		OWLEntity class_Phyloreference = optClassPhyloreference.iterator().next();
		
		// Find all classes that have an rdf:type of http://phyloinformatics.net/phyloref.owl#Phyloreference.
		Set<OWLNamedIndividual> phylorefs = reasoner.getInstances(class_Phyloreference.asOWLClass(), true).getFlattened();
		
		// Okay, time to start testing! Each phyloreference counts as one test.
		TapProducer tapProducer = TapProducerFactory.makeTap13Producer();
		TestSet testSet = new TestSet();
		testSet.setPlan(new Plan(phylorefs.size()));
		
		// Get some additional properties.
		OWLAnnotationProperty labelAnnotationProperty = manager.getOWLDataFactory().getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		OWLDataProperty tnrsMatchedNameProperty = manager.getOWLDataFactory().getOWLDataProperty(IRI.create("http://phylotastic.org/terms/tnrs.rdf#matchedName"));
		OWLObjectPropertyExpression inPhylogenyProperty = 
			manager.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://example.org/TBD#inPhylogeny"));
			// TODO encode this into the ontology, but for now we'll take the inverse property and invert it.
		
		// Loop
		int testNumber = 0;
		int countSuccess = 0;
		int countFailure = 0;
		for(OWLNamedIndividual phyloref: phylorefs) {
			testNumber++;
			TestResult result = new TestResult();
			result.setTestNumber(testNumber);
			boolean testFailed = false;
			
			// Write out test information.
			String phylorefLabel = OWLHelper.getAnnotationLiteralsForEntity(ontology, phyloref, labelAnnotationProperty, Arrays.asList("en"))
				.stream().collect(Collectors.joining("; "));
			
			if(phylorefLabel.equals("")) phylorefLabel = phyloref.getIRI().toString();
			result.setDescription("Phyloreference '" + phylorefLabel + "'");
			
			// Which nodes are this phyloreference resolved to? 
			OWLClass phylorefAsClass = manager.getOWLDataFactory().getOWLClass(phyloref.getIRI()); 
			Set<OWLNamedIndividual> nodes = reasoner.getInstances(phylorefAsClass, false).getFlattened();
			
			if(nodes.isEmpty()) {
				result.setStatus(StatusValues.NOT_OK);
				result.addComment(new Comment("No nodes matched"));
				testFailed = true;
			}
			
			// Each phyloref should only resolve to one node on each phylogeny. So let's
			// map each phyloref to its phylogeny. Also, let's grab their labels while we're
			// at it.
			Map<OWLNamedIndividual, Set<OWLLiteral>> matchedNamesByNode = new HashMap<>();
			
			for(OWLNamedIndividual node: nodes) {
				// Which phylogeny does this node belong to?
				// The relation is Object(<phylogeny> tbd:nodes <node>)
				
				Set<OWLNamedIndividual> phylogenies = reasoner.getObjectPropertyValues(node, inPhylogenyProperty).getFlattened();
				if(phylogenies.isEmpty()) {
					result.addComment(new Comment("Node '" + node.getIRI().toString() + "' is not found in any phylogeny."));
					testFailed = true;
					break;
				}
				
				if(phylogenies.size() > 1) {
					result.addComment(new Comment(
						"Node '" + node.getIRI().toString() + "' is found in " + phylogenies.size() + " phylogenies: " +
						phylogenies.stream()
							.map(phylogeny -> phylogeny.asOWLNamedIndividual().getIRI().toString())
							.collect(Collectors.joining("; "))
					));
					testFailed = true;
					break;
				}
				
				// What are the labels associated with these nodes?
				// The relation is Datatype(<node> tnrs:matchedName <value>)
				matchedNamesByNode.put(
					node, 
					node.getDataPropertyValues(tnrsMatchedNameProperty, ontology)
				);
			}
			
			// Okay, which labels do we have? We fail if we have more than one OWLLiteral
			Set<OWLLiteral> labels = matchedNamesByNode.values().stream().flatMap(n -> n.stream()).collect(Collectors.toSet());
			if(labels.isEmpty()) {
				result.addComment(new Comment("No matched nodes have a label"));
				testFailed = true;
			} else if(labels.size() > 1) {
				// This is okay IF at least one of the nodes is named after this phyloreference.
				
				List<String> otherLabels = new LinkedList<>();
				
				int matchCount = 0;
				for(OWLLiteral label: labels) {
					if(label.getLiteral().equals(phylorefLabel)) matchCount++;
					else otherLabels.add(label.getLiteral());
				}
				
				String otherLabelsStr = otherLabels.stream().collect(Collectors.joining("; "));
				
				if(matchCount > 0) {
					result.addComment(new Comment("Node matched on " + matchCount + " phylogenies; other labels found included: " + otherLabelsStr));
				} else {
					result.addComment(new Comment("Nodes matched with multiple labels: " + otherLabelsStr));
					testFailed = true;
				}
			} else {
				OWLLiteral onlyOne = labels.iterator().next();
				String label = onlyOne.getLiteral();
				
				if(label.equals(phylorefLabel)) {
					result.addComment(new Comment("Node label '" + label + "' matched phyloref label '" + phylorefLabel + "'"));
				} else {
					result.addComment(new Comment("Node label '" + label + "' did not match phyloref label '" + phylorefLabel + "'"));
					testFailed = true;
				}
			}
			
			if(testFailed) {
				// Yay, failure!
				countFailure++;
				result.setStatus(StatusValues.NOT_OK);
				testSet.addTapLine(result);
			} else {
				// Oh no, success!
				countSuccess++;
				result.setStatus(StatusValues.OK);
				testSet.addTapLine(result);
			}
		}
		
		System.out.println(tapProducer.dump(testSet));
		System.err.println("Testing complete:" + countSuccess + " successes, " + countFailure + " failures");
		
		// Exit.
		if(countSuccess == 0) System.exit(-1);
		System.exit(countFailure);
	}
}
