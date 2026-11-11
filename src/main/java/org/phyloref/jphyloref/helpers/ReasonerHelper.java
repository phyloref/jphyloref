package org.phyloref.jphyloref.helpers;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.Version;

/**
 * The ReasonerHelper provides methods to help create and manage OWL Reasoners, and to allow the
 * user to choose a different reasoner to carry out any specified task.
 *
 * @author Gaurav Vaidya
 */
public class ReasonerHelper {
  /** A map of reasoner names and the corresponding reasoner factory */
  private static Map<String, OWLReasonerFactory> reasonerFactories = new HashMap<>();

  static {
    /*
     * Set up a list of reasoner names and their corresponding reasoner factory.
     */
    reasonerFactories.put("null", null);
    reasonerFactories.put("elk", new ElkReasonerFactory());
  }

  /** Get reasoner factory by name. */
  public static OWLReasonerFactory getReasonerFactory(String name) {
    // Look it up.
    if (reasonerFactories.containsKey(name)) {
      return reasonerFactories.get(name);
    }

    // If all else fails, throw an exception.
    throw new IllegalArgumentException(
        "No reasoner named '"
            + name
            + "'; must be one of: "
            + reasonerFactories.keySet().toString());
  }

  /** Get all reasoner factories. */
  public static Map<String, OWLReasonerFactory> getReasonerFactories() {
    return reasonerFactories;
  }

  public static String getReasonerNameAndVersion(OWLReasonerFactory factory) {
    if (factory == null) return "No reasoner used";

    String versionString;
    try {
      OWLReasoner reasoner =
          factory.createNonBufferingReasoner(
              OWLManager.createOWLOntologyManager().createOntology());
      Version version = reasoner.getReasonerVersion();
      versionString =
          version.getMajor()
              + "."
              + version.getMinor()
              + "."
              + version.getBuild()
              + "."
              + version.getPatch();
    } catch (NumberFormatException e) {
      // Strangely, ELK appears to throw one of these when trying to figure out
      // its own version string. If so, let's try to extract the version from
      // the error string.
      String errorMessage = e.getMessage();
      if (errorMessage.startsWith("For input string: \"") && errorMessage.endsWith("\"")) {
        versionString = errorMessage.substring(19, errorMessage.length() - 1);
      } else {
        // Report the error as the version string.
        versionString = "(error: " + errorMessage + ")";
      }
    } catch (OWLOntologyCreationException e) {
      // There was an error creating the OWL Ontology.
      versionString = "(error: " + e + ")";
    } catch (UnsatisfiedLinkError err) {
      // Reasoners that use Java Native Interface (JNI) libraries
      // will throw this exception if a native method could not be
      // found. Trapping it here allows us to display the entire
      // list of reasoners without disruption.
      versionString = "(JNI library not linked)";
    }

    return factory.getReasonerName() + "/" + versionString;
  }

  /**
   * Return an OWLReasoner based on the command-line settings.
   *
   * <p>For now, we only look for one setting -- '--reasoner' -- and identify a reasoner based on
   * that, but in the future we might support additional options that allow you to configure the
   * reasoner.
   */
  public static OWLReasonerFactory getReasonerFactoryFromCmdLine(CommandLine cmdLine) {
    if (cmdLine.hasOption("reasoner")) {
      return getReasonerFactory(cmdLine.getOptionValue("reasoner"));
    } else {
      // No reasoner provided? Default to Elk.
      return new ElkReasonerFactory();
    }
  }

  /**
   * Create a reasoner for an ontology from a factory that getReasonerFactoryFromCmdLine() returned.
   * Commands look the factory up before loading the ontology, so that an unknown `--reasoner` name
   * is reported as such rather than as a problem with the ontology.
   *
   * @return The reasoner, or null if the factory is null (`--reasoner null`). Callers must pass a
   *     null reasoner on to PhylorefHelper (which then reads only asserted axioms), and must
   *     dispose of a non-null one in a finally block: the webserver runs indefinitely, and ELK
   *     keeps worker threads alive until its reasoner is disposed.
   */
  public static OWLReasoner createReasoner(OWLReasonerFactory factory, OWLOntology ontology) {
    if (factory == null) return null;
    return factory.createReasoner(ontology);
  }

  /** Add command line options that can be read by getReasonerFromCmdLine() */
  public static void addCommandLineOptions(Options opts) {
    opts.addOption(
        "reasoner", "reasoner", true, "The reasoner to be used (use help to see list of options)");
  }
}
