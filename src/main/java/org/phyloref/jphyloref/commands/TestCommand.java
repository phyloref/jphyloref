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
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.util.AutoIRIMapper;
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
 * Testing output is produced using the Test Anything Protocol, which has nice
 * libraries in both Python and Java.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 *
 */
public class TestCommand implements Command {
    /**
     * This command is named "test". It should be
     * invoked as "java -jar jphyloref.jar test ..."
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
        opts.addOption(
            "nr", "no-reasoner", false,
            "Turn off reasoning (all tests will fail!)"
        );
    }

    /**
     * Given an input ontology, reason over it and determine if nodes are
     * identified correctly. It provides output on System.out using the
     * Test Anything Protocol (TAP: https://testanything.org/).
     *
     * @param cmdLine The command line options provided to this command.
     */
    @Override
    public void execute(CommandLine cmdLine) throws RuntimeException {
        // Extract command-line options
        String str_input = cmdLine.getOptionValue("input");
        boolean flag_no_reasoner = cmdLine.hasOption("no-reasoner");

        if(str_input == null && cmdLine.getArgList().size() > 1) {
            // No 'input'? Maybe it's just provided as a left-over option?
            str_input = cmdLine.getArgList().get(1);
        }

        if(str_input == null) {
            throw new IllegalArgumentException("Error: no input ontology specified (use '-i input.owl')");
        }

        // Create File object to load
        File inputFile = new File(str_input);
        System.err.println("Input: " + inputFile);

        // Set up an OWL Ontology Manager to work with.
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        // Is purl.obolibrary.org down? No worries, you can access local copies
        // of your ontologies in the 'ontologies/' folder.
        AutoIRIMapper mapper = new AutoIRIMapper(new File("ontologies"), true);
        System.err.println("Found local ontologies: " + mapper.getOntologyIRIs());
        manager.addIRIMapper(mapper);

        // Load the ontology using OWLManager.
        OWLOntology ontology;
        try {
            ontology = manager.loadOntologyFromOntologyDocument(inputFile);
        } catch (OWLOntologyCreationException ex) {
            throw new RuntimeException("Could not load ontology '" + inputFile + "': " + ex);
        }

        // Ontology loaded.
        System.err.println("Loaded ontology: " + ontology);

        // Reason over the loaded ontology -- but only if the user wants that!
        JFactReasonerConfiguration config = new JFactReasonerConfiguration();
        JFactReasoner reasoner = null;
        Set<OWLNamedIndividual> phylorefs;

        // Get a list of all phyloreferences.
        if(!flag_no_reasoner) reasoner = new JFactReasoner(ontology, config, BufferingMode.BUFFERING);
        phylorefs = PhylorefHelper.getPhyloreferences(ontology, reasoner);

        // Okay, time to start testing! Each phyloreference counts as one test.
        // TAP (https://testanything.org/) can be read by downstream software
        // to determine which phyloreferences resolved correctly and which did not.
        TapProducer tapProducer = TapProducerFactory.makeTap13Producer();
        TestSet testSet = new TestSet();
        testSet.setPlan(new Plan(phylorefs.size()));

        // Preload some terms we need to use in the following code.
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        // Terms associated with phyloreferences
        OWLAnnotationProperty labelAnnotationProperty = dataFactory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
        OWLDataProperty expectedPhyloreferenceNamedProperty = dataFactory.getOWLDataProperty(PhylorefHelper.IRI_NAME_OF_EXPECTED_PHYLOREF);
        OWLObjectProperty unmatchedSpecifierProperty = dataFactory.getOWLObjectProperty(PhylorefHelper.IRI_PHYLOREF_UNMATCHED_SPECIFIER);
        // OWLDataProperty specifierDefinitionProperty = dataFactory.getOWLDataProperty(PhylorefHelper.IRI_CLADE_DEFINITION);

        // Terms assocated with publication status
        OWLAnnotationProperty pso_holdsStatusInTime = dataFactory.getOWLAnnotationProperty(IRI.create("http://purl.org/spar/pso/holdsStatusInTime"));
        OWLAnnotationProperty pso_withStatus = dataFactory.getOWLAnnotationProperty(IRI.create("http://purl.org/spar/pso/withStatus"));
        OWLAnnotationProperty tvc_atTime = dataFactory.getOWLAnnotationProperty(IRI.create("http://www.essepuntato.it/2012/04/tvc/atTime"));
        OWLDataProperty timeinterval_hasIntervalStartDate = dataFactory.getOWLDataProperty(IRI.create("http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalStartDate"));
        OWLDataProperty timeinterval_hasIntervalEndDate = dataFactory.getOWLDataProperty(IRI.create("http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalEndDate"));

        // Count the number of test results.
        int testNumber = 0;
        int countSuccess = 0;
        int countFailure = 0;
        int countTODO = 0;
        int countSkipped = 0;

        // Test each phyloreference individually.
        for(OWLNamedIndividual phyloref: phylorefs) {
            // Prepare a TestResult object in which we can store the results of
            // testing this particular phyloreference.
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

            // Which nodes did this phyloreference resolved to?
            OWLClass phylorefAsClass = manager.getOWLDataFactory().getOWLClass(phyloref.getIRI());
            Set<OWLNamedIndividual> nodes;
            if(reasoner != null) {
                // Use the reasoner to determine which nodes are members of this phyloref as a class
            	nodes = reasoner.getInstances(phylorefAsClass, false).getFlattened();
            } else {
                // No reasoner? We can also determine which nodes have been directly stated to
                // be members of this phyloref as a class. This allows us to read a pre-reasoned
                // OWL file and test whether phyloreferences resolved as expected.
                nodes = new HashSet<>();
                Set<OWLClassAssertionAxiom> classAssertions = ontology.getAxioms(AxiomType.CLASS_ASSERTION);

                for(OWLClassAssertionAxiom classAssertion: classAssertions) {
                    // Does this assertion involve this phyloreference as a class and a named individual?
                    if(
                        classAssertion.getIndividual().isNamed() &&
                        classAssertion.getClassesInSignature().contains(phylorefAsClass)
                    ) {
                        // If so, then the individual is a phyloreferences!
                        nodes.add(classAssertion.getIndividual().asOWLNamedIndividual());
                    }
                }
            }

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

                    // Is this blank? If so, let's use the node's IRI as its label so we can debug issues with resolution.
                    if(expectedPhylorefsNamed.isEmpty()) {
                        nodeLabel = node.getIRI().toString();
                    }

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

            // Retrieve holdsStatusInTime to determine the active status of this phyloreference.
            Set<OWLAnnotation> holdsStatusInTime = phylorefAsClass.getAnnotations(ontology, pso_holdsStatusInTime);

            // Instead of checking which time interval were are in, we take a simpler approach: we look for all
            // statuses that have a start date but not an end date, i.e. those which have yet to end.
            Set<IRI> statuses = new HashSet<>();
            for(OWLAnnotation statusInTime: holdsStatusInTime) {
                // Each statusInTime entry should have one status (pso:withStatus)
                // and a number of time intervals (tvc:atTime). We collect all
                // statusues and test to see if any of those time intervals are
                // "incomplete", i.e. they have a start date but no end date.
            	Set<IRI> currentStatuses = new HashSet<>();
                boolean hasIntervalStartDate = false;
                boolean hasIntervalEndDate = false;

            	for(OWLAnonymousIndividual indiv_statusInTime: statusInTime.getAnonymousIndividuals()) {
                    for(OWLAnnotationAssertionAxiom axiom: ontology.getAnnotationAssertionAxioms(indiv_statusInTime)) {
                        if(axiom.getProperty().equals(tvc_atTime)) {
                            for(OWLAnonymousIndividual indiv_atTime: axiom.getValue().getAnonymousIndividuals()) {
                                for(OWLDataPropertyAssertionAxiom axiom_interval: ontology.getDataPropertyAssertionAxioms(indiv_atTime)) {
                                    // Look for timeinterval:hasIntervalStartDate and timeinterval:hasIntervalEndDate data properties.
                                    if(axiom_interval.getProperty().equals(timeinterval_hasIntervalStartDate)) {
                                        hasIntervalStartDate = true;
                                    }
                                    if(axiom_interval.getProperty().equals(timeinterval_hasIntervalEndDate)) {
                                        hasIntervalEndDate = true;
                                    }
                                }
                            }
                        }

                        else if(axiom.getProperty().equals(pso_withStatus)) {
                            currentStatuses.add((IRI)axiom.getValue());
                        } else {
                            System.err.println("Unknown axiom: " + axiom);
                            System.exit(-1);
                        }
                    }
            	}

                // Did the current statuses have a start date but no end date, i.e. are they currently active?
                if(hasIntervalStartDate && !hasIntervalEndDate) {
                    statuses.addAll(currentStatuses);
                }
            }

            // System.err.println("Active statuses: " + statuses);

            // Based on the phyloreference status, we can determine whether or not
            // we expect the phyloreference to actually resolve: we do if there
            // were no statuses, or if the statuses include submitted or published.
            // In all other cases, we're not expecting the phyloreference to resolve.
            boolean flag_expected_to_resolve = (
                statuses.isEmpty() ||
                statuses.contains(IRI.create("http://purl.org/spar/pso/submitted")) ||
                statuses.contains(IRI.create("http://purl.org/spar/pso/published"))
            );

            // Determine if this phyloreference has failed or succeeded.
            if(testFailed) {
            	if(unmatched_specifiers.isEmpty()) {
                    if(flag_expected_to_resolve) {
                        // We expect this phyloreference to resolve. Report a failure.
                        countFailure++;
                        result.setStatus(StatusValues.NOT_OK);
                        testSet.addTapLine(result);
                    } else {
                        // No, we do not expect this phyloreference to resolve. Report a TODO.
                        countTODO++;
                        result.setStatus(StatusValues.NOT_OK);
                        result.setDirective(new Directive(DirectiveValues.TODO, "Phyloreference did not resolve, but has status " + statuses));
                        testSet.addTapLine(result);
                    }
            	} else {
                    // Okay, it's a failure, but we do know that there are unmatched specifiers.
                    // So mark it as a to-do.
                    countTODO++;
                    result.setStatus(StatusValues.NOT_OK);
                    result.setDirective(new Directive(DirectiveValues.TODO, "Phyloreference could not be tested, as one or more specifiers did not match."));
                    if(!statuses.contains(IRI.create("http://purl.org/spar/pso/draft"))) {
                        result.addComment(new Comment(
                            "Since specifiers remain unmatched, this phyloreference should have a status of 'pso:draft' but instead its status is " + statuses
                        ));
                    }
                    testSet.addTapLine(result);
            	}
            } else {
                // Oh no, success!
                countSuccess++;
                result.setStatus(StatusValues.OK);
                if(!flag_expected_to_resolve) {
                    result.addComment(new Comment(
                        "Phyloreference resolved correctly but was not expected to resolve; status should be changed to 'pso:submitted' from " + statuses
                    ));
                }
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
