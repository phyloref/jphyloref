package org.phyloref.jphyloref.commands;

import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.phyloref.jphyloref.helpers.OWLHelper;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationObjectVisitor;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubAnnotationPropertyOfAxiom;
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
        opts.addOption(
    		"nr", "no-reasoner", false,
    		"Turn off reasoning (all tests will fail!)"
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
        // Determine which input ontology should be read
        String str_input = cmdLine.getOptionValue("input");
        boolean flag_no_reasoner = cmdLine.hasOption("no-reasoner");

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

        // Set up an OWL Ontology Manager to work with.
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        // Is purl.obolibrary.org down? No worries, we store local copies of all our ontologies!
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
        if(flag_no_reasoner) {
        	phylorefs = PhylorefHelper.getPhyloreferences(ontology);
        } else {
        	reasoner = new JFactReasoner(ontology, config, BufferingMode.BUFFERING);
        	phylorefs = PhylorefHelper.getPhyloreferences(ontology, reasoner);
        }

        // Okay, time to start testing! Each phyloreference counts as one test.
        // TAP (https://testanything.org/) can be read by downstream software
        // to determine which phyloreferences resolved correctly and which did not.
        TapProducer tapProducer = TapProducerFactory.makeTap13Producer();
        TestSet testSet = new TestSet();
        testSet.setPlan(new Plan(phylorefs.size()));

        // Get some additional properties.
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        OWLAnnotationProperty pso_holdsStatusInTime = dataFactory.getOWLAnnotationProperty(IRI.create("http://purl.org/spar/pso/holdsStatusInTime"));
        OWLAnnotationProperty pso_withStatus = dataFactory.getOWLAnnotationProperty(IRI.create("http://purl.org/spar/pso/withStatus"));
        OWLAnnotationProperty tvc_atTime = dataFactory.getOWLAnnotationProperty(IRI.create("http://www.essepuntato.it/2012/04/tvc/atTime"));
        OWLDataProperty timeinterval_hasIntervalStartDate = dataFactory.getOWLDataProperty(IRI.create("http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalStartDate"));
        OWLDataProperty timeinterval_hasIntervalEndDate = dataFactory.getOWLDataProperty(IRI.create("http://www.ontologydesignpatterns.org/cp/owl/timeinterval.owl#hasIntervalEndDate"));

        // System.err.println("Adding axiom hasIntervalStartDate: " + manager.addAxiom(ontology, dataFactory.getOWLDeclarationAxiom(timeinterval_hasIntervalStartDate)));
//        System.err.println("Adding axiom hasIntervalEndDate: " + manager.addAxiom(ontology, dataFactory.getOWLDeclarationAxiom(timeinterval_hasIntervalEndDate)));

        // Get some properties ready before-hand so we don't have to reload
        // them on every loop.
        OWLAnnotationProperty labelAnnotationProperty = dataFactory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
        OWLDataProperty expectedPhyloreferenceNameProperty = dataFactory.getOWLDataProperty(PhylorefHelper.IRI_NAME_OF_EXPECTED_PHYLOREF);
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

            // Which nodes did this phyloreference resolved to?
            OWLClass phylorefAsClass = manager.getOWLDataFactory().getOWLClass(phyloref.getIRI());
            Set<OWLNamedIndividual> nodes;
            if(reasoner == null) {
            	nodes = new HashSet();
            } else {
            	nodes = reasoner.getInstances(phylorefAsClass, false).getFlattened();
            }

            if(nodes.isEmpty()) {
                // Phyloref resolved to no nodes at all.
                result.setStatus(StatusValues.NOT_OK);
                result.addComment(new Comment("No nodes matched."));
                testFailed = true;
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
                    result.addComment(new Comment("None of the " + nodes.size() + " resolved nodes are expected to resolve to phyloreferences."));
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
                        result.addComment(new Comment("Phyloreference resolved to " + matchCount + " nodes with the expected phyloreference label '" + phylorefLabel + "'; other nodes expected phyloreferences: " + otherLabelsStr));
                    } else {
                        result.addComment(new Comment("Phyloreference did not resolve to the expected phyloreference label '" + phylorefLabel + "', but did resolve to multiple expected phyloreferences: " + otherLabelsStr));
                        testFailed = true;
                    }

                } else {
                    // We have exactly one expected phyloref name -- but is it the right one?
                    OWLLiteral onlyOne = distinctExpectedPhylorefNames.iterator().next();
                    String label = onlyOne.getLiteral();

                    if(label.equals(phylorefLabel)) {
                        result.addComment(new Comment("Resolved node label '" + label + "' identical to expected phyloreference label '" + phylorefLabel + "'"));
                    } else {
                        result.addComment(new Comment("Resolved node label '" + label + "' differs from expected phyloreference label '" + phylorefLabel + "'"));
                        testFailed = true;
                    }
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
            	Set<IRI> currentStatuses = new HashSet<>();
        		boolean hasIntervalStartDate = false;
        		boolean hasIntervalEndDate = false;

            	for(OWLAnonymousIndividual indiv_statusInTime: statusInTime.getAnonymousIndividuals()) {
            		System.err.println(" - statusInTime data prop assert axioms: " + ontology.getDataPropertyAssertionAxioms(indiv_statusInTime));

            		// System.err.println(" - indiv_statusInTime: " + indiv_statusInTime);
            		for(OWLAnnotationAssertionAxiom axiom: ontology.getAnnotationAssertionAxioms(indiv_statusInTime)) {
            			/* System.err.println(
        					"Processing axiom: " + axiom.getProperty() + " == " + pso_withStatus +
        					" (" + axiom.getProperty().equals(pso_withStatus) + ")"
            			);*/

                		if(axiom.getProperty().equals(tvc_atTime)) {
                			System.err.println("Axiom: " + axiom);

                			for(OWLAnnotation annot: axiom.getAnnotations()) {
                				System.err.println(" - axiom annotation: " + annot);
                			}

                			for(OWLAnonymousIndividual indiv_atTime: axiom.getValue().getAnonymousIndividuals()) {
                				System.err.println(" - indiv_atTime: " + indiv_atTime);

                				System.err.println(" - axioms: " + ontology.getAxioms(indiv_atTime));
                				System.err.println(" - Signature: " + indiv_atTime.getSignature());
                				System.err.println(" - data prop assert axioms: " + ontology.getDataPropertyAssertionAxioms(indiv_atTime));
                				System.err.println(" - class assert axioms: " + ontology.getClassAssertionAxioms(indiv_atTime));

                				for(OWLDataPropertyAssertionAxiom axiom_interval: ontology.getDataPropertyAssertionAxioms(indiv_atTime)) {
                					System.err.println(" - axiom interval: " + axiom_interval);
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
                		}
                	}
            	}

        		// Did the current statuses have a start date but no end date, i.e. are they currently active?
        		if(hasIntervalStartDate && !hasIntervalEndDate) {
        			statuses.addAll(currentStatuses);
        		}
            }

            System.err.println("Active statuses: " + statuses);

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
