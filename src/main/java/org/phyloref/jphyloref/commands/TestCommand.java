package org.phyloref.jphyloref.commands;

import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.phyloref.jphyloref.helpers.OWLHelper;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
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
 * At the moment, this works on OWL ontologies, but there's really no reason we
 * couldn't test the labeled.json file directly! Maybe at a later date?
 *
 * I can't resist using the Test Anything Protocol here, which has nice
 * libraries in both Python and Java.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 *
 */
public class TestCommand implements Command {
    /**
     * This command is named "test". It should be 
     * involved "java -jar jphyloref.jar test ..."
     */
    @Override
    public String getName() { 
        return "test"; 
    }
    
    /**
     * @return A description of this command.
     */
    @Override
    public String getDescription() { 
        return "Test the phyloreferences in the provided ontology to determine if they resolved correctly."; 
    }

    /**
     * Add command-line options specific to this command.
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
        OWLDataProperty expectedPhyloreferenceNameProperty = dataFactory.getOWLDataProperty(PhylorefHelper.IRI_NAME_OF_EXPECTED_PHYLOREF);
        OWLObjectProperty unmatchedSpecifierProperty = dataFactory.getOWLObjectProperty(PhylorefHelper.IRI_PHYLOREF_UNMATCHED_SPECIFIER);
        OWLDataProperty specifierDefinitionProperty = dataFactory.getOWLDataProperty(PhylorefHelper.IRI_CLADE_DEFINITION);
        
        // Test each phyloreference individually.
        int testNumber = 0;
        int countSuccess = 0;
        int countFailure = 0;
        
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

            // Which nodes are this phyloreference resolved to?
            OWLClass phylorefAsClass = manager.getOWLDataFactory().getOWLClass(phyloref.getIRI()); 
            Set<OWLNamedIndividual> nodes = reasoner.getInstances(phylorefAsClass, false).getFlattened();

            if(nodes.isEmpty()) {
                // Phyloref resolved to no nodes at all.
                // But wait! Maybe that's because it has unmatched specifiers?
                
                Set<OWLNamedIndividual> specifiers = reasoner.getObjectPropertyValues(phyloref, unmatchedSpecifierProperty).getFlattened();
                if(specifiers.isEmpty()) {
                    // No unmatched specifiers (that we know about) and YET
                    // the phyloreference failed to resolve.
                    result.setStatus(StatusValues.NOT_OK);
                    result.addComment(new Comment("No nodes matched, no known unmatched specifiers."));
                    testFailed = true;
                } else {
                    // Okay, the phyloreference didn't resolve, but now we know
                    // why -- because these specifiers did not resolve.
                    result.addComment(new Comment("No nodes matched, but " + specifiers.size() + " specifiers did not match."));
                    // We don't explicitly mark this as a test failure by itself.
                }
            } else {
                // Make a list of every expected phyloreference for input node.
                Map<OWLNamedIndividual, Set<OWLLiteral>> expectedPhyloreferencesByNode = new HashMap<>();

                for(OWLNamedIndividual node: nodes) {
                    // What are the expected phyloreferences associated with these nodes?
                    expectedPhyloreferencesByNode.put(
                        node, 
                        node.getDataPropertyValues(expectedPhyloreferenceNameProperty, ontology)
                    );
                }

                // Flatten expected phyloreference names from each Node into a 
                // single set of unique expected phyloreference names.
                Set<OWLLiteral> distinctExpectedPhylorefNames = expectedPhyloreferencesByNode.values()
                    .stream().flatMap(n -> n.stream())
                    .collect(Collectors.toSet());
                
                // How many distinct expected phyloref names do we have?
                if(distinctExpectedPhylorefNames.isEmpty()) {
                    result.addComment(new Comment("None of the " + nodes.size() + " matched nodes are expected to resolve to phyloreferences"));
                    testFailed = true;
                    
                } else if(distinctExpectedPhylorefNames.size() > 1) {
                    // This is okay IF at least one of the nodes is expected to resolve to this phyloreference.
                    List<String> otherLabels = new LinkedList<>();

                    int matchCount = 0;
                    for(OWLLiteral label: distinctExpectedPhylorefNames) {
                        if(label.getLiteral().equals(phylorefLabel)) matchCount++;
                        else otherLabels.add(label.getLiteral());
                    }

                    String otherLabelsStr = otherLabels.stream().collect(Collectors.joining("; "));

                    if(matchCount > 0) {
                        result.addComment(new Comment("Node matched on " + matchCount + " nodes; other nodes expected phyloreferences: " + otherLabelsStr));
                    } else {
                        result.addComment(new Comment("Nodes matched with multiple taxa: " + otherLabelsStr));
                        testFailed = true;
                    }
                    
                } else {
                    // We have exactly one expected phyloref name -- but is it the right one?
                    OWLLiteral onlyOne = distinctExpectedPhylorefNames.iterator().next();
                    String label = onlyOne.getLiteral();

                    if(label.equals(phylorefLabel)) {
                        result.addComment(new Comment("Node label '" + label + "' matched phyloref taxon '" + phylorefLabel + "'"));
                    } else {
                        result.addComment(new Comment("Node label '" + label + "' did not match phyloref taxon '" + phylorefLabel + "'"));
                        testFailed = true;
                    }
                }
            }

            // Determine if this phyloreference has failed or succeeded.
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

        // Exit with error unless we have zero failures.
        if(countSuccess == 0) System.exit(-1);
        System.exit(countFailure);
    }
}
