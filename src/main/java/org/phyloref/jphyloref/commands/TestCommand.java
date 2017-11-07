package org.phyloref.jphyloref.commands;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.phyloref.jphyloref.helpers.OWLHelper;
import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
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
    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getDescription() {
        return "Test the phyloreferences in the provided ontology to determine if they resolved correctly.";
    }

    @Override
    public void addCommandLineOptions(Options opts) {
        opts.addOption("i", "input", true, "The input ontology to read in RDF/XML (can also be provided without the '-i')");
        opts.addOption("debug_specifiers", false, "Identify unmatched specifiers");
    }
    
    /**
     * Execute this command with the provided command line options.
     *
     * @param cmdLine The command line provided to this command.
     */
    @Override
    public void execute(CommandLine cmdLine) throws RuntimeException {
        // Read command line arguments.
        String str_debug_specifiers = cmdLine.getOptionValue("debug_specifiers");
        boolean flag_debug_specifiers = (str_debug_specifiers != null);

        String str_input = cmdLine.getOptionValue("input");
        if (str_input == null && cmdLine.getArgList().size() > 1) {
            // No 'input'? Maybe it's just provided as a left-over option?
            str_input = cmdLine.getArgList().get(1);
        }
        if (str_input == null) {
            throw new RuntimeException("Error: no input ontology specified (use '-i input.owl')");
        }

        File inputFile = new File(str_input);
        if (!inputFile.exists() || !inputFile.canRead()) {
            throw new RuntimeException("Error: cannot read from input ontology '" + str_input + "'");
        }

        System.err.println("Input: " + str_input);

        // Load the ontology into inputOntology.
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();
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

        // Find all phyloreferences.
        Set<OWLNamedIndividual> phylorefs = PhylorefHelper.getPhyloreferences(ontology, reasoner);

        // Okay, time to start testing! Each phyloreference counts as one test.
        TapProducer tapProducer = TapProducerFactory.makeTap13Producer();
        TestSet testSet = new TestSet();
        testSet.setPlan(new Plan(phylorefs.size()));

        // Loop
        int testNumber = 0;
        int countSuccess = 0;
        int countFailure = 0;
        for (OWLNamedIndividual phyloref : phylorefs) {
            testNumber++;
            TestResult result = new TestResult();
            result.setTestNumber(testNumber);
            boolean testFailed = false;

            // Write out test information.
            String phylorefLabel = OWLHelper.getLabel(phyloref, ontology).stream().distinct().sorted().collect(Collectors.joining("; "));

            if (phylorefLabel.equals("")) {
                phylorefLabel = phyloref.getIRI().toString();
            }
            result.setDescription("Phyloreference '" + phylorefLabel + "'");

            // Which nodes are this phyloreference resolved to?
            OWLClass phyloref_asClass = dataFactory.getOWLClass(phyloref.getIRI());
            Set<OWLNamedIndividual> nodes = reasoner.getInstances(phyloref_asClass, false).getFlattened();

            if (nodes.isEmpty()) {
                result.setStatus(StatusValues.NOT_OK);
                result.addComment(new Comment("No nodes matched"));
                testFailed = true;

            } else {
                // Each phyloref should only resolve to one node on each phylogeny. So let's
                // map each phyloref to its phylogeny. Also, let's grab their labels while we're
                // at it.
                Map<OWLNamedIndividual, Set<OWLLiteral>> matchedNamesByNode = new HashMap<>();

                for (OWLNamedIndividual node : nodes) {
                    // Which phylogeny does this node belong to?
                    // The relation is Object(<phylogeny> tbd:nodes <node>)
                    
                    OWLObjectPropertyExpression inPhylogenyProperty = 
                        manager.getOWLDataFactory().getOWLObjectProperty(IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#inPhylogeny"));
                    OWLDataProperty matchedNameProperty = 
                        manager.getOWLDataFactory().getOWLDataProperty(IRI.create("http://vocab.phyloref.org/phyloref/testcase.owl#matchedName"));
                    
                    Set<OWLNamedIndividual> phylogenies = reasoner.getObjectPropertyValues(node, inPhylogenyProperty).getFlattened();
                    if (phylogenies.isEmpty()) {
                        result.addComment(new Comment("Node '" + node.getIRI().toString() + "' is not found in any phylogeny."));
                        testFailed = true;
                        break;
                    }

                    if (phylogenies.size() > 1) {
                        result.addComment(new Comment(
                                "Node '" + node.getIRI().toString() + "' is found in " + phylogenies.size() + " phylogenies: "
                                + phylogenies.stream()
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
                            node.getDataPropertyValues(matchedNameProperty, ontology)
                    );
                }

                // Okay, which labels do we have? We fail if we have more than one OWLLiteral
                Set<OWLLiteral> labels = matchedNamesByNode.values().stream().flatMap(n -> n.stream()).collect(Collectors.toSet());
                if (labels.isEmpty()) {
                    result.addComment(new Comment("No matched nodes have matched names: " + nodes));
                    testFailed = true;
                } else if (labels.size() > 1) {
                    // This is okay IF at least one of the nodes is named after this phyloreference.

                    List<String> otherLabels = new LinkedList<>();

                    int matchCount = 0;
                    for (OWLLiteral label : labels) {
                        if (label.getLiteral().equals(phylorefLabel)) {
                            matchCount++;
                        } else {
                            otherLabels.add(label.getLiteral());
                        }
                    }

                    String otherLabelsStr = otherLabels.stream().collect(Collectors.joining("; "));

                    if (matchCount > 0) {
                        result.addComment(new Comment("Node matched on " + matchCount + " phylogenies; other matched names found included: " + otherLabelsStr));
                    } else {
                        result.addComment(new Comment("Nodes matched with multiple matched names: " + otherLabelsStr));
                        testFailed = true;
                    }
                } else {
                    OWLLiteral onlyOne = labels.iterator().next();
                    String label = onlyOne.getLiteral();

                    if (label.equals(phylorefLabel)) {
                        result.addComment(new Comment("Node matched name '" + label + "' matched phyloref label '" + phylorefLabel + "'"));
                    } else {
                        result.addComment(new Comment("Node matched name '" + label + "' did not match phyloref label '" + phylorefLabel + "'"));
                        testFailed = true;
                    }
                }
            }

            if (testFailed) {
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
        if (countSuccess == 0) {
            System.exit(-1);
        }
        System.exit(countFailure);
    }
}
