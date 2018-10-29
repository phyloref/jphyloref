package org.phyloref.jphyloref;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A unit test for running the JPhyloRef application. */
public class AppTest {
  /** Test whether JPhyloRef has a version number. */
  @Test
  public void phylorefVersion() {
    JPhyloRef jphyloref = new JPhyloRef();
    assertNotEquals(jphyloref.VERSION, "");
  }

  /** Test whether we can run the "help" command. */
  @Test
  @DisplayName("Test 'java -jar jphyloref.jar help'")
  public void phylorefCommandLineHelp() {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output));

    final ByteArrayOutputStream error = new ByteArrayOutputStream();
    System.setErr(new PrintStream(error));

    // Run 'help' and see if we get the correct response.
    JPhyloRef jphyloref = new JPhyloRef();
    jphyloref.execute(new String[] {"help"});

    try {
      String outputStr = output.toString("UTF-8");
      assertTrue(
          outputStr.matches("(?s)^JPhyloRef/[\\w\\-\\.]+ OWLAPI/[\\d\\.\\-]+.*"),
          "We get the version information in the first line: " + outputStr);
      assertTrue(
          outputStr.contains("Synopsis: jphyloref <command> <options>"),
          "We see the synopsis in the output: " + outputStr);
      assertTrue(
          outputStr.contains("'jfact': JFact/"),
          "We see JFact reported in the output: " + outputStr);
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
    }
  }

  /** Test how JPhyloRef responds to an unknown command. */
  @Test
  @DisplayName("Test 'java -jar jphyloref.jar unknown', which should fail")
  public void phylorefCommandLineUnknown() {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    System.setOut(new PrintStream(output));

    final ByteArrayOutputStream error = new ByteArrayOutputStream();
    System.setErr(new PrintStream(error));

    // Run 'version' and see if we get the correct response.
    JPhyloRef jphyloref = new JPhyloRef();
    jphyloref.execute(new String[] {"unknown"});

    try {
      assertEquals("", output.toString("UTF-8"));
      assertEquals("Error: command 'unknown' has not been implemented.\n", error.toString("UTF-8"));
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
    }
  }
}
