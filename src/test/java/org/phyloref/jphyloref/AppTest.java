package org.phyloref.jphyloref;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A unit test for running the JPhyloRef application. */
@DisplayName("The JPhyloRef application")
class AppTest {
  static JPhyloRef jphyloref = new JPhyloRef();
  static ByteArrayOutputStream output = new ByteArrayOutputStream();
  static ByteArrayOutputStream error = new ByteArrayOutputStream();

  /** Set up input and output streams */
  @BeforeAll
  static void setupIO() {
    System.setOut(new PrintStream(output));
    System.setErr(new PrintStream(error));
  }

  /** Reset input and output streams between test runs */
  @BeforeEach
  void resetIO() {
    output.reset();
    error.reset();
  }

  /** Test whether JPhyloRef has a version number. */
  @Test
  @DisplayName("includes a version number")
  void phylorefVersion() {
    JPhyloRef jphyloref = new JPhyloRef();
    assertNotEquals(jphyloref.VERSION, "");
  }

  /** Test whether we get the "help" output if we run JPhyloRef without any command line. */
  @Test
  @DisplayName("provides 'help' output with an empty command line.")
  void phylorefEmptyCommandLine() {
    // Run 'help' and see if we get the correct response.
    jphyloref.execute(new String[] {});
    try {
      checkHelpOutput(output.toString("UTF-8"));
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
    }
  }

  /** This method checks whether the output of the Help command is sensible */
  private void checkHelpOutput(String outputStr) {
    assertTrue(
        outputStr.matches("(?s)^JPhyloRef/[\\w\\-\\.]+ OWLAPI/[\\d\\.\\-]+.*"),
        "We get the version information in the first line: " + outputStr);
    assertTrue(
        outputStr.contains("Synopsis: jphyloref <command> <options>"),
        "We see the synopsis in the output: " + outputStr);
  }

  /** Test how JPhyloRef responds to an unknown command. */
  @Test
  @DisplayName("fails correctly for unknown command line commands")
  void phylorefCommandLineUnknown() {
    // Run 'version' and see if we get the correct response.
    jphyloref.execute(new String[] {"unknown"});

    try {
      assertEquals("", output.toString("UTF-8"));
      assertEquals(
          "[main] ERROR org.phyloref.jphyloref.JPhyloRef - Command 'unknown' has not been implemented.\n",
          error.toString("UTF-8"));
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
    }
  }

  /** Test whether JPhyloRef works with a malformed command line. */
  @Test
  @DisplayName("fails correctly when given a malformed command line")
  void phylorefCommandLineMalformed() {
    // Run '----' and see if we get the correct response.
    jphyloref.execute(new String[] {"----"});

    try {
      assertEquals("", output.toString("UTF-8"));
      assertEquals(
          "[main] ERROR org.phyloref.jphyloref.JPhyloRef - Could not parse command line options: org.apache.commons.cli.UnrecognizedOptionException: Unrecognized option: ----\n",
          error.toString("UTF-8"));
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
    }
  }
}
