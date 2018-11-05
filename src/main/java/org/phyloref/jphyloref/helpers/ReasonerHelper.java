package org.phyloref.jphyloref.helpers;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.Version;
import uk.ac.manchester.cs.factplusplus.owlapiv3.FaCTPlusPlusReasonerFactory;
import uk.ac.manchester.cs.jfact.JFactFactory;

/**
 * The ReasonerHelper provides methods to help create and manage OWL Reasoners, and to allow the
 * user to choose a different reasoner to carry out any specified task.
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
    reasonerFactories.put("null", null);
    reasonerFactories.put("jfact", new JFactFactory());
    reasonerFactories.put("fact++", new FaCTPlusPlusReasonerFactory());
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
    } catch (OWLOntologyCreationException e) {
      versionString = "(undefined)";
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
      // No reasoner provided? Default to JFact.
      return new JFactFactory();
    }
  }

  /** Add command line options that can be read by getReasonerFromCmdLine() */
  public static void addCommandLineOptions(Options opts) {
    opts.addOption(
        "reasoner", "reasoner", true, "The reasoner to be used (use help to see list of options)");
  }
}
