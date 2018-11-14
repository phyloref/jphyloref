package org.phyloref.jphyloref.helpers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

/** A unit test for running the JPhyloRef application. */
@DisplayName("ReasonerHelper")
class ReasonerHelperTest {
  @Nested
  @DisplayName("has methods for accessing reasoner factories that")
  class ReasonerFactoriesTest {
    @Test
    @DisplayName("can provide a list of reasoner factories")
    void canAccessList() {
      Map<String, OWLReasonerFactory> list = ReasonerHelper.getReasonerFactories();
      assertFalse(list.isEmpty(), "List of reasoner factories should not be empty");
    }

    @Test
    @DisplayName("can access a null reasoner")
    void canAccessNull() {
      OWLReasonerFactory nullFactory = ReasonerHelper.getReasonerFactory("null");
      assertNull(nullFactory);
      assertEquals("No reasoner used", ReasonerHelper.getReasonerNameAndVersion(nullFactory));
    }

    @Test
    @DisplayName("can access the JFact reasoner")
    void canAccessJFact() {
      OWLReasonerFactory jFactFactory = ReasonerHelper.getReasonerFactory("jfact");
      assertEquals("uk.ac.manchester.cs.jfact.JFactFactory", jFactFactory.getClass().getName());
      assertTrue(
          ReasonerHelper.getReasonerNameAndVersion(jFactFactory).startsWith("JFact/1."),
          "'jfact' should refer to JFact version 1.x");
    }

    @Test
    @DisplayName("fails correctly when given an incorrect reasoner name")
    void failsCorrectly() {
      assertThrows(
          IllegalArgumentException.class,
          () -> {
            ReasonerHelper.getReasonerFactory("incorrect");
          },
          "No reasoner named 'incorrect'; must be one of: "
              + ReasonerHelper.getReasonerFactories().keySet().toString());
    }

    @Test
    @DisplayName("can read the reasoner from the command line")
    void canReadFromCmdLine() {
      Options cmdLineOptions = new Options();
      ReasonerHelper.addCommandLineOptions(cmdLineOptions);

      try {
        CommandLine cmdLine =
            new DefaultParser().parse(cmdLineOptions, new String[] {"--reasoner", "null"});
        assertNull(ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine));

        cmdLine = new DefaultParser().parse(cmdLineOptions, new String[] {"--reasoner", "jfact"});
        OWLReasonerFactory jFactFactory = ReasonerHelper.getReasonerFactoryFromCmdLine(cmdLine);
        assertEquals("uk.ac.manchester.cs.jfact.JFactFactory", jFactFactory.getClass().getName());
        assertTrue(
            ReasonerHelper.getReasonerNameAndVersion(jFactFactory).startsWith("JFact/1."),
            "'--reasoner jfact' should load JFact version 1.x");

      } catch (ParseException ex) {
        throw new RuntimeException("Pre-written command line arguments could not be parsed: " + ex);
      }
    }
  }
}
