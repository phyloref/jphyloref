package org.phyloref.jphyloref.commands;

import java.io.File;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.reasoner.InferenceType;
import uk.ac.manchester.cs.jfact.JFactReasoner;
import uk.ac.manchester.cs.jfact.kernel.options.JFactReasonerConfiguration;

/**
 * Reason over the provided ontology and provide both ABox and TBox.
 *
 * <p>Eventually, I'd like to set this up to output the complete reasoned ontology, so that we can
 * poke at the result using rdflib in Python. For now, however, it works as a simple
 * proof-of-concept that phyloreferencing resolution is occuring.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class ReasonCommand implements Command {
  public String getName() {
    return "reason";
  }

  public String getDescription() {
    return "Reason over the provided ontology and provide both ABox and TBox.";
  }

  public void addCommandLineOptions(Options opts) {
    opts.addOption("i", "input", true, "The input ontology to read in RDF/XML");
    opts.addOption("o", "output", true, "Where to write the reasoned ontology in RDF/XML");
  }

  /** Execute this command with the provided command line options. */
  public int execute(CommandLine cmdLine) {
    String str_input = cmdLine.getOptionValue("input");
    String str_output = cmdLine.getOptionValue("output");

    if (str_input == null) {
      System.err.println("Error: no input ontology specified (use '-i input.owl')");
      return 1;
    }

    File inputFile = new File(str_input);
    if (!inputFile.exists() || !inputFile.canRead()) {
      System.err.println("Error: cannot read from input ontology '" + str_input + "'");
      return 1;
    }

    System.err.println("Input: " + str_input);
    System.err.println("Output: " + str_output);

    // Load the ontology into inputOntology.
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLOntology ontology;
    try {
      ontology = manager.loadOntologyFromOntologyDocument(inputFile);
    } catch (OWLOntologyCreationException ex) {
      System.err.println("Could not load ontology '" + inputFile + "': " + ex);
      return 1;
    }

    // Ontology loaded.
    System.err.println("Loaded ontology: " + ontology);

    // Now to reason.
    JFactReasonerConfiguration config = new JFactReasonerConfiguration();
    JFactReasoner reasoner = new JFactReasoner(ontology, config, BufferingMode.BUFFERING);

    reasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS);

    System.err.println("Precomputing complete.");

    // Finally, tell us how each node was characterized.
    IRI iri_Node = IRI.create("http://purl.obolibrary.org/obo/CDAO_0000140");
    Set<OWLEntity> optClassNode = ontology.getEntitiesInSignature(iri_Node);
    if (optClassNode.isEmpty())
      throw new RuntimeException("No Nodes found in ontology '" + ontology + "'");
    OWLEntity class_Node = optClassNode.iterator().next();

    // Get all nodes belonging to class Node.
    Set<OWLNamedIndividual> individuals =
        reasoner.getInstances(class_Node.asOWLClass(), false).getFlattened();

    System.out.println(individuals.size() + " nodes found:");
    for (OWLNamedIndividual node : individuals) {
      System.out.print(" - " + node.getIRI().getFragment() + ": ");

      for (OWLClass cl : reasoner.getTypes(node, true).getFlattened()) {
        System.out.print(cl + "; ");
      }
      System.out.println("");
    }

    return 0;
  }
}
