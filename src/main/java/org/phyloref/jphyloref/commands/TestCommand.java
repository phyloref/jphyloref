package org.phyloref.jphyloref.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.eclipse.rdf4j.rio.RDFParser;
import org.phyloref.jphyloref.helpers.JSONLDHelper;
import org.phyloref.jphyloref.helpers.OWLHelper;
import org.phyloref.jphyloref.helpers.PhylorefHelper;
import org.phyloref.jphyloref.helpers.ReasonerHelper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tap4j.model.Comment;
import org.tap4j.model.Directive;
import org.tap4j.model.Plan;
import org.tap4j.model.TestResult;
import org.tap4j.model.TestSet;
import org.tap4j.producer.TapProducer;
import org.tap4j.producer.TapProducerFactory;
import org.tap4j.util.DirectiveValues;
import org.tap4j.util.StatusValues;

/**
 * Test whether the phyloreferences in the provided ontology resolve correctly. This currently
 * supports RDF/XML input only, but we will eventually modify this to support PHYX files directly.
 *
 * <p>Testing output is produced using the Test Anything Protocol, which has nice libraries in both
 * Python and Java.
 *
 * @author Gaurav Vaidya
 */
public class TestCommand implements Command {
  /** Set up a logger to use for providing logging. */
  private static final Logger logger = LoggerFactory.getLogger(TestCommand.class);

  /** This command is named "test". It should be invoked as "java -jar jphyloref.jar test ..." */
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
        "i",
        "input",
        true,
        "The input ontology to read in RDF/XML or JSON-LD (can also be provided without the '-i').");

    opts.addOption(
        "j",
        "jsonld",
        false,
        "Treat the input file as a JSON-LD file. Files with a '.json' or '.jsonld' extension will automatically be treated as a JSON-LD file.");
  }

  /**
   * Given an input ontology, reason over it and determine if nodes are identified correctly. It
   * provides output on System.out using the Test Anything Protocol (TAP:
   * https://testanything.org/).
   *
   * @param cmdLine The command line options provided to this command.
   */
  @Override
  public int execute(CommandLine cmdLine) throws RuntimeException {
    // Extract command-line options
    String inputFilename = cmdLine.getOptionValue("input");

    if (inputFilename == null && cmdLine.getArgList().size() > 1) {
      // No 'input'? Maybe it's just provided as a left-over option?
      inputFilename = cmdLine.getArgList().get(1);
    }

    if (inputFilename == null) {
      throw new IllegalArgumentException("Error: no input ontology specified (use '-i input.owl')");
    }

    // If the input filename is '-', we should read the ontology from STDIN instead.
    InputStream inputStreamToReadFrom = null;
    if (inputFilename.equals("-")) {
      inputStreamToReadFrom = System.in;
    } else {
      try {
        inputStreamToReadFrom = new FileInputStream(inputFilename);
      } catch (FileNotFoundException ex) {
        logger.error("Could not open input file '{}': {}", inputFilename, ex);
        return 1;
      }
    }

    // Report the name of the file being tested.
    logger.info("Input: {}", inputFilename);

    // Set up an OWL Ontology Manager to work with.
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

    // Is purl.obolibrary.org down? No worries, you can access local copies
    // of your ontologies in the 'ontologies/' folder.
    AutoIRIMapper mapper = new AutoIRIMapper(new File("ontologies"), true);
    logger.info("Found local ontologies: {}", mapper.getOntologyIRIs());
    manager.addIRIMapper(mapper);

    // Is this a JSON or JSON-LD file?
    OWLOntology ontology;
    String inputFileLowercase = inputFilename.toLowerCase();

    String defaultURIPrefix = null;
    try {
      if (cmdLine.hasOption("jsonld")
          || inputFileLowercase.endsWith(".json")
          || inputFileLowercase.endsWith(".jsonld")) {
        // Use the JSONLD Helper to load the ontology.
        ontology = manager.createOntology();
        RDFParser parser = JSONLDHelper.createRDFParserForOntology(ontology);

        // Set a default URI prefix in case it is needed.
        defaultURIPrefix = "http://example.org/jphyloref";

        // Read from the provided input stream (either STDIN or a file).
        parser.parse(inputStreamToReadFrom, defaultURIPrefix);

      } else {
        // Load the ontology using OWLManager, by reading from the provided
        // input stream (either STDIN or a file).
        ontology = manager.loadOntologyFromOntologyDocument(inputStreamToReadFrom);
      }
    } catch (OWLOntologyCreationException ex) {
      logger.error("Could not create ontology '{}': {}", inputFilename, ex);
      return 1;
    } catch (IOException ex) {
      logger.error("Could not read and load ontology '{}': {}", inputFilename, ex);
      return 1;
    }

    // Ontology loaded.
    logger.info("Loaded ontology: {}", ontology);

    // Reason over the loaded ontology -- but only if the user wants that!
    // Set up an OWLReasoner to work with.
    OWLReasonerFactory reasonerFactory = ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine);
    OWLReasoner reasoner = null;
    if (reasonerFactory != null) reasoner = reasonerFactory.createReasoner(ontology);

    // Get a list of all phyloreferences.
    Set<OWLClass> phylorefs = PhylorefHelper.getPhyloreferences(ontology, reasoner);
    logger.info("Phyloreferences identified: {}", phylorefs);

    // Okay, time to start testing! Each phyloreference counts as one test.
    // TAP (https://testanything.org/) can be read by downstream software
    // to determine which phyloreferences resolved correctly and which did not.
    TapProducer tapProducer = TapProducerFactory.makeTap13Producer();
    TestSet testSet = new TestSet();
    testSet.setPlan(new Plan(phylorefs.size()));
    testSet.addComment(new Comment("From file: " + inputFilename));
    testSet.addComment(
        new Comment(
            "Using reasoner: "
                + ReasonerHelper.getReasonerNameAndVersion(
                    ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine))));

    // Preload some terms we need to use in the following code.
    OWLDataFactory dataFactory = manager.getOWLDataFactory();

    // Some classes we will use.
    OWLClass classCDAONode = dataFactory.getOWLClass(PhylorefHelper.IRI_CDAO_NODE);

    // Terms associated with phyloreferences
    OWLAnnotationProperty labelAnnotationProperty =
        dataFactory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
    OWLDataProperty expectedPhyloreferenceNamedProperty =
        dataFactory.getOWLDataProperty(PhylorefHelper.IRI_NAME_OF_EXPECTED_PHYLOREF);
    OWLObjectProperty unmatchedSpecifierProperty =
        dataFactory.getOWLObjectProperty(PhylorefHelper.IRI_PHYLOREF_UNMATCHED_SPECIFIER);
    // OWLDataProperty specifierDefinitionProperty =
    // dataFactory.getOWLDataProperty(PhylorefHelper.IRI_CLADE_DEFINITION);

    // Count the number of test results.
    int testNumber = 0;
    int countSuccess = 0;
    int countFailure = 0;
    int countTODO = 0;
    int countSkipped = 0;

    // Test each phyloreference individually.
    for (OWLClass phyloref : phylorefs) {
      // Prepare a TestResult object in which we can store the results of
      // testing this particular phyloreference.
      testNumber++;
      TestResult result = new TestResult();
      result.setTestNumber(testNumber);
      boolean testFailed = false;

      // Collect English labels for the phyloreference.
      Optional<String> opt_phylorefLabel =
          OWLHelper.getAnnotationLiteralsForEntity(
                  ontology, phyloref, labelAnnotationProperty, Arrays.asList("en"))
              .stream()
              .findFirst();

      String phylorefLabel;
      // Use a phyloref label if we could find one.
      if (opt_phylorefLabel.isPresent()) phylorefLabel = opt_phylorefLabel.get();
      // If we don't have labels, use the IRI of the phyloref.
      else phylorefLabel = phyloref.getIRI().toString();
      result.setDescription("Phyloreference '" + phylorefLabel + "'");

      // Which nodes did this phyloreference resolve to?
      Set<OWLNamedIndividual> nodes = PhylorefHelper.getNodesInClass(phyloref, ontology, reasoner);
      // System.err.println("Phyloreference <" + phyloref + "> has nodes: " + nodes);

      // Get a list of phyloref statuses for this phyloreference.
      List<PhylorefHelper.PhylorefStatus> statuses =
          PhylorefHelper.getStatusesForPhyloref(phyloref, ontology);

      // Look for all unmatched specifiers reported for this phyloreference.
      Set<OWLAxiom> axioms = new HashSet<>(EntitySearcher.getReferencingAxioms(phyloref, ontology));
      Set<OWLNamedIndividual> unmatched_specifiers = new HashSet<>();
      for (OWLAxiom axiom : axioms) {
        if (axiom.containsEntityInSignature(unmatchedSpecifierProperty)) {
          // This axiom references this phyloreference AND the unmatched specifier property!
          // Therefore, any NamedIndividuals that are not phyloref should be added to
          // unmatched_specifiers!
          for (OWLNamedIndividual ni : axiom.getIndividualsInSignature()) {
            if (ni != phyloref) unmatched_specifiers.add(ni);
          }
        }
      }

      // Report all unmatched specifiers
      for (OWLNamedIndividual unmatched_specifier : unmatched_specifiers) {
        Set<String> unmatched_specifier_label =
            OWLHelper.getAnnotationLiteralsForEntity(
                ontology, unmatched_specifier, labelAnnotationProperty, Arrays.asList("en"));
        if (!unmatched_specifier_label.isEmpty()) {
          result.addComment(
              new Comment("Specifier '" + unmatched_specifier_label + "' is marked as unmatched."));
        } else {
          result.addComment(
              new Comment(
                  "Specifier '"
                      + unmatched_specifier.getIRI().getShortForm()
                      + "' is marked as unmatched."));
        }
      }

      // Instead of checking which time interval we are currently in, we take a simpler approach:
      // we look for all statuses asserted to be "active", i.e. those with a start time but no end
      // time.
      boolean flag_expected_to_resolve = false;

      List<PhylorefHelper.PhylorefStatus> activeStatuses =
          statuses
              .stream()
              .filter(ps -> ps.getIntervalStart() != null && ps.getIntervalEnd() == null)
              .collect(Collectors.toList());

      // If there are no active statuses, we default to assuming that we expect phyloreferences to
      // resolve.
      if (activeStatuses.isEmpty()) flag_expected_to_resolve = true;
      else
        // If there are active statuses, we default to assuming that we expect phyloreferences NOT
        // to resolve,
        // unless they are actively in the "submitted" or "published" statuses.
        flag_expected_to_resolve =
            activeStatuses
                .stream()
                .anyMatch(
                    ps ->
                        ps.getStatus().equals(PhylorefHelper.IRI_PSO_SUBMITTED)
                            || ps.getStatus().equals(PhylorefHelper.IRI_PSO_PUBLISHED));

      // Time to figure out whether we resolved nodes correctly!
      if (nodes.isEmpty()) {
        // This phyloref resolved to no nodes at all.
        result.setStatus(StatusValues.NOT_OK);
        result.addComment(new Comment("No nodes matched."));
        testSet.addTapLine(result);
        countFailure++;
        continue;

      } else {
        // Report which nodes were resolved.
        result.addComment(
            new Comment("Resolved nodes: " + removeDefaultURIPrefixes(nodes, defaultURIPrefix)));

        // Given a phyloreference class, determine all the nodes that we expect to be
        // resolved by that phyloreference class.
        OWLClassExpression expectedNodesExpr =
            dataFactory.getOWLObjectSomeValuesFrom(
                dataFactory.getOWLObjectProperty(PhylorefHelper.IRI_OBI_IS_SPECIFIED_OUTPUT_OF),
                dataFactory.getOWLObjectSomeValuesFrom(
                    dataFactory.getOWLObjectProperty(PhylorefHelper.IRI_OBI_HAS_SPECIFIED_INPUT),
                    phyloref));

        Set<OWLNamedIndividual> expectedNodes = new HashSet<>();
        if (reasoner == null) {
          // If there's no reasoner, we could look for individuals specifically
          // marked as expected (see PhylorefHelper for an example). However, we
          // don't need to implement this until we actually have a need for this.
          throw new RuntimeException("Testing without reasoner not yet implemented.");
        }

        // Get direct and indirect instances of the expectedNodesExpr.
        expectedNodes = reasoner.getInstances(expectedNodesExpr, false).getFlattened();
        result.addComment(
            new Comment(
                "Expected nodes: " + removeDefaultURIPrefixes(expectedNodes, defaultURIPrefix)));

        // Identify two sets of nodes: those we expected but that weren't resolved,
        // and those that we resolved that weren't expected.
        HashSet<OWLNamedIndividual> expectedButNotResolved = new HashSet<>(expectedNodes);
        expectedButNotResolved.removeAll(nodes);

        HashSet<OWLNamedIndividual> resolvedButNotExpected = new HashSet<>(nodes);
        resolvedButNotExpected.removeAll(expectedNodes);

        // If every node we resolved to was a node we expected to resolve to, this
        // was a success.
        if (expectedButNotResolved.isEmpty() && resolvedButNotExpected.isEmpty()) {
          result.setStatus(StatusValues.OK);
          // If this phyloref is marked as not expected to resolve, we can let
          // the user know that they should mark it
          if (!flag_expected_to_resolve) {
            result.addComment(
                new Comment(
                    "This phyloref resolved as expected, and should be marked as pso:submitted instead of: "
                      + activeStatuses));
          }
          testSet.addTapLine(result);
          countSuccess++;
          continue;
        }

        // These are all failures. But are they TODOs?
        result.setStatus(StatusValues.NOT_OK);
        boolean flagTODO = false;

        if (!flag_expected_to_resolve) {
          result.setDirective(
              new Directive(
                  DirectiveValues.TODO,
                  "Phyloreference is not expected to resolve as it has a status of "
                      + activeStatuses));
          flagTODO = true;
        }

        if (!unmatched_specifiers.isEmpty()) {
          result.setDirective(
              new Directive(
                  DirectiveValues.TODO,
                  "Phyloreference could not be tested, as one or more specifiers did not match."));
          flagTODO = true;
        }

        if (!resolvedButNotExpected.isEmpty()) {
          result.addComment(
              new Comment(
                  "Some nodes were resolved but were not expected: " + resolvedButNotExpected));
        }

        if (!expectedButNotResolved.isEmpty()) {
          result.addComment(
              new Comment(
                  "Some nodes were expected but were not resolved: " + expectedButNotResolved));
        }

        if (flagTODO) {
          countTODO++;
        } else {
          countFailure++;
        }

        testSet.addTapLine(result);
        continue;
      }
    }

    System.out.println(tapProducer.dump(testSet));
    System.err.println(
        "Testing complete:"
            + countSuccess
            + " successes, "
            + countFailure
            + " failures, "
            + countTODO
            + " failures marked TODO, "
            + countSkipped
            + " skipped.");

    // Dispose of the reasoner.
    reasoner.dispose();

    // Exit with error unless we have zero failures.
    if (countSuccess == 0) return -1;
    return countFailure;
  }

  /* Helper methods */

  /** Given a list of IRIs, remove the defaultURIPrefix if one is set. */
  private List<String> removeDefaultURIPrefixes(
      Set<OWLNamedIndividual> indivs, String defaultURIPrefix) {
    if (defaultURIPrefix == null) {
      // Just convert the hasIRIs into strings and return.
      return indivs.stream().map(indiv -> indiv.getIRI().toString()).collect(Collectors.toList());
    }

    // Remove the default URI prefix if one exists.
    return indivs
        .stream()
        .map(
            indiv -> {
              String iriString = indiv.getIRI().toString();
              if (iriString.startsWith(defaultURIPrefix))
                return iriString.substring(defaultURIPrefix.length());
              return iriString;
            })
        .collect(Collectors.toList());
  }
}
