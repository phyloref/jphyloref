package org.phyloref.jphyloref.commands;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.phyloref.jphyloref.helpers.OWLHelper;
import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.tap4j.model.Comment;
import org.tap4j.model.Directive;
import org.tap4j.model.Plan;
import org.tap4j.model.TestResult;
import org.tap4j.model.TestSet;
import org.tap4j.producer.TapProducer;
import org.tap4j.producer.TapProducerFactory;
import org.tap4j.util.DirectiveValues;
import org.tap4j.util.StatusValues;

import uk.ac.manchester.cs.jfact.JFactReasoner;
import uk.ac.manchester.cs.jfact.kernel.options.JFactReasonerConfiguration;

/**
 * Test whether the phyloreferences in the provided ontology resolve correctly.
 * This currently supports RDF/XML input only, but we will eventually modify
 * this to support PHYX files directly.
 *
 * I use the Test Anything Protocol here, which has nice libraries in both 
 * Python and Java.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 *
 */
public class TestCommand implements Command {
    /**
     * This command is named Test. It should be 
     * involved "java -jar jphyloref.jar test ..."
     */

    @Override
    public String getName() { 
        return "test"; 
    }
    
    /**
     * A description of the Test command.
     * 
     * @return A description of this command.
     */
    @Override
    public String getDescription() { 
        return "Test the phyloreferences in the provided ontology to determine if they resolved correctly."; 
    }

    /**
     * Add command-line options specific to this command. There is only one:
     * -i or --input can be used to set the RDF/XML file to read.
     * 
     * @param opts The command-line options to modify for this command.
     */
    @Override
    public void addCommandLineOptions(Options opts) {
        opts.addOption(
            "i", "input", true, 
            "The input ontology to read in RDF/XML (can also be provided without the '-i')"
        );
    }

    /**
     * Given an input ontology, reason over it and determine if nodes are
     * identified correctly. It provides output using the Test Anything 
     * Protocol (TAP: https://testanything.org/). 
     * 
     * @param cmdLine The command line options provided to this command.
     */
    @Override
    public void execute(CommandLine cmdLine) throws RuntimeException {
        // Determine which input ontology should be read, 
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

        System.err.println("Input: " + inputFile);

        // Load the ontology using OWLManager.
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology;
        try {
            ontology = manager.loadOntologyFromOntologyDocument(inputFile);
        } catch (OWLOntologyCreationException ex) {
            throw new RuntimeException("Could not load ontology '" + inputFile + "': " + ex);
        }

        // Ontology loaded.
        System.err.println("Loaded ontology: " + ontology);

        // Reason over the loaded ontology.
        JFactReasonerConfiguration config = new JFactReasonerConfiguration();
        JFactReasoner reasoner = new JFactReasoner(ontology, config, BufferingMode.BUFFERING);

        // Get a list of all phyloreferences.
        Set<OWLNamedIndividual> phylorefs = PhylorefHelper.getPhyloreferences(ontology, reasoner);

        // Okay, time to start testing! Each phyloreference counts as one test.
        // TAP (https://testanything.org/) can be read by downstream software
        // to determine which phyloreferences resolved correctly and which did not.
        TapProducer tapProducer = TapProducerFactory.makeTap13Producer();
        TestSet testSet = new TestSet();
        testSet.setPlan(new Plan(phylorefs.size()));

        // Get some additional properties.
        OWLDataFactory dataFactory = manager.getOWLDataFactory();
        
        // Get some properties ready before-hand so we don't have to reload
        // them on every loop.
        OWLAnnotationProperty labelAnnotationProperty = dataFactory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
        OWLDataProperty expectedPhyloreferenceNamedProperty = dataFactory.getOWLDataProperty(PhylorefHelper.IRI_NAME_OF_EXPECTED_PHYLOREF);
        OWLObjectProperty unmatchedSpecifierProperty = dataFactory.getOWLObjectProperty(PhylorefHelper.IRI_PHYLOREF_UNMATCHED_SPECIFIER);
        // OWLDataProperty specifierDefinitionProperty = dataFactory.getOWLDataProperty(PhylorefHelper.IRI_CLADE_DEFINITION);
        
        // Test each phyloreference individually.
        int testNumber = 0;
        int countSuccess = 0;
        int countFailure = 0;
        int countTODO = 0;
        int countSkipped = 0;
        
        for(OWLNamedIndividual phyloref: phylorefs) {
            testNumber++;
            TestResult result = new TestResult();
            result.setTestNumber(testNumber);
            boolean testFailed = false;

            // Collect English labels for the phyloreference.
            Optional<String> opt_phylorefLabel = OWLHelper.getAnnotationLiteralsForEntity(
                ontology, 
                phyloref, 
                labelAnnotationProperty, 
                Arrays.asList("en")
            ).stream().findFirst();

            String phylorefLabel;
            if(opt_phylorefLabel.isPresent()) 
                phylorefLabel = opt_phylorefLabel.get();
            else 
                phylorefLabel = phyloref.getIRI().toString();
            result.setDescription("Phyloreference '" + phylorefLabel + "'");

            // Which nodes did this phyloreference resolve to?
            OWLClass phylorefAsClass = manager.getOWLDataFactory().getOWLClass(phyloref.getIRI()); 
            Set<OWLNamedIndividual> nodes = reasoner.getInstances(phylorefAsClass, false).getFlattened();

            if(nodes.isEmpty()) {
                // Phyloref resolved to no nodes at all.
                result.setStatus(StatusValues.NOT_OK);
                result.addComment(new Comment("No nodes matched."));
                testFailed = true;
            } else {
                // Look for nodes where either its label or its expected phyloreference label
                // is equal to the phyloreference we are currently processing.
            	Set<String> nodeLabelsWithExpectedPhylorefs = new HashSet<>();
            	Set<String> nodeLabelsWithoutExpectedPhylorefs = new HashSet<>();
            	
                for(OWLNamedIndividual node: nodes) {
                	// Get a list of all expected phyloreference labels from the OWL file.
                	Set<String> expectedPhylorefsNamed = node.getDataPropertyValues(expectedPhyloreferenceNamedProperty, ontology)
                		.stream()
                		.map(literal -> literal.getLiteral()) // We ignore languages for now.
                		.collect(Collectors.toSet());
                	
                	// Add the label of the node as well.
                	Set<String> nodeLabels = OWLHelper.getLabelsInEnglish(node, ontology);
                	expectedPhylorefsNamed.addAll(nodeLabels);
                	
                	// Build a new node label that describes this node.
                	String nodeLabel = new StringBuilder()
                		.append("[")
                		.append(String.join(", ", expectedPhylorefsNamed))
                		.append("]")
                		.toString();
                	
                	// Does this node have an expected phyloreference identical to the phyloref being tested?
                	if(expectedPhylorefsNamed.contains(phylorefLabel)) {
                		nodeLabelsWithExpectedPhylorefs.add(nodeLabel);
                	} else {
                		nodeLabelsWithoutExpectedPhylorefs.add(nodeLabel);
                	}
                }
                
                // What happened?
                if(!nodeLabelsWithExpectedPhylorefs.isEmpty() && !nodeLabelsWithoutExpectedPhylorefs.isEmpty()) {
                	// We found nodes that expected this phyloref, as well as nodes that did not -- success!
                	result.addComment(new Comment("The following nodes were matched and expected this phyloreference: " + String.join("; ", nodeLabelsWithExpectedPhylorefs)));
                	result.addComment(new Comment("Also, the following nodes were matched but did not expect this phyloreference: " + String.join("; ", nodeLabelsWithoutExpectedPhylorefs)));
                } else if(!nodeLabelsWithExpectedPhylorefs.isEmpty()) {
                	// We only found nodes that expected this phyloref -- success!
                	result.addComment(new Comment("The following nodes were matched and expected this phyloreference: " + String.join("; ", nodeLabelsWithExpectedPhylorefs)));
                } else if(!nodeLabelsWithoutExpectedPhylorefs.isEmpty()) {
                	// We only have nodes that did not expect this phyloref -- failure!
                	result.addComment(new Comment("The following nodes were matched but did not expect this phyloreference: " + String.join("; ", nodeLabelsWithoutExpectedPhylorefs)));
                    testFailed = true;
                } else {
                	// No nodes matched. This should have been caught earlier, but just in case.
                	throw new RuntimeException("No nodes were matched, which should have been caught earlier. Programmer error!");
                }                
            }
            
            // Look for all unmatched specifiers reported for this phyloreference
            Set<OWLAxiom> axioms = phyloref.getReferencingAxioms(ontology);
            Set<OWLNamedIndividual> unmatched_specifiers = new HashSet<>();
            for(OWLAxiom axiom: axioms) {
            	if(axiom.containsEntityInSignature(unmatchedSpecifierProperty)) {
            		// This axiom references this phyloreference AND the unmatched specifier property!
            		// Therefore, any NamedIndividuals that are not phyloref should be added to
            		// unmatched_specifiers!
            		for(OWLNamedIndividual ni: axiom.getIndividualsInSignature()) {
            			if(ni != phyloref) unmatched_specifiers.add(ni);
            		}
            	}
            }
            
            // Report all unmatched specifiers
            for(OWLNamedIndividual unmatched_specifier: unmatched_specifiers) {
                Set<String> unmatched_specifier_label = OWLHelper.getAnnotationLiteralsForEntity(ontology, unmatched_specifier, labelAnnotationProperty, Arrays.asList("en"));
                if(!unmatched_specifier_label.isEmpty()) {
                    result.addComment(new Comment("Specifier '" + unmatched_specifier_label + "' is marked as unmatched."));
                } else {
                    result.addComment(new Comment("Specifier '" + unmatched_specifier.getIRI().getShortForm() + "' is marked as unmatched."));
                }
            }

            // Determine if this phyloreference has failed or succeeded.
            if(testFailed) {
            	if(unmatched_specifiers.isEmpty()) {
	                // Yay, failure!
	                countFailure++;
	                result.setStatus(StatusValues.NOT_OK);
	                testSet.addTapLine(result);
            	} else {
            		// Okay, it's a failure, but we do know that there are unmatched specifiers.
            		// So mark it as a to-do.
            		countTODO++;
            		result.setStatus(StatusValues.NOT_OK);
            		result.setDirective(new Directive(DirectiveValues.TODO, "Phyloreference could not be tested, as one or more specifiers did not match."));
            		testSet.addTapLine(result);
            	}
            } else {
                // Oh no, success!
                countSuccess++;
                result.setStatus(StatusValues.OK);
                testSet.addTapLine(result);
            }
        }

        System.out.println(tapProducer.dump(testSet));
        System.err.println("Testing complete:" + countSuccess + " successes, " + countFailure + " failures, " + countTODO + " failures marked TODO, " + countSkipped + " skipped.");

        // Exit with error unless we have zero failures.
        if(countSuccess == 0) System.exit(-1);
        System.exit(countFailure);
    }
}
