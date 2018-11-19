package org.phyloref.jphyloref;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A unit test for the TestCommand class */
@DisplayName("TestCommandTest")
class TestCommandTest {
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

  @Nested
  @DisplayName("can test OWL files")
  class TestingOWLFile {
    @Test
    @DisplayName("successfully tests simple OWL files")
    void testSimpleOWLFiles() {
      // Run 'test dummy1.owl' and see if we get the correct response.
      int exitCode =
          jphyloref.execute(new String[] {"test", "src/test/resources/phylorefs/dummy1.owl"});

      String outputStr, errorStr;
      try {
        outputStr = output.toString("UTF-8");
        errorStr = error.toString("UTF-8");
      } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      }

      assertEquals(0, exitCode);
      assertTrue(
          errorStr.contains("Input: src/test/resources/phylorefs/dummy1.owl"),
          "Make sure JPhyloRef reports the name of the file being processed");
      assertTrue(
          errorStr.endsWith(
              "Testing complete:1 successes, 0 failures, 0 failures marked TODO, 0 skipped.\n"),
          "Make sure the testing was successful.");
      assertEquals(
          "1..1\nok 1 Phyloreference '1'\n# The following nodes were matched and expected this phyloreference: [1]\n\n",
          outputStr);
    }
  }

  @Nested
  @DisplayName("can test JSON-LD files")
  class TestingJSONLDFile {
    @Test
    @DisplayName("successfully tests simple JSON-LD files")
    void testSimpleJSONLDFiles() {
      // Run 'test dummy1.jsonld' and see if we get the correct response.
      int exitCode =
          jphyloref.execute(new String[] {"test", "src/test/resources/phylorefs/dummy1.jsonld"});

      String outputStr, errorStr;
      try {
        outputStr = output.toString("UTF-8");
        errorStr = error.toString("UTF-8");
      } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      }

      assertEquals(0, exitCode);
      assertTrue(
          errorStr.contains("Input: src/test/resources/phylorefs/dummy1.jsonld"),
          "Make sure JPhyloRef reports the name of the file being processed");
      assertTrue(
          errorStr.endsWith(
              "Testing complete:1 successes, 0 failures, 0 failures marked TODO, 0 skipped.\n"),
          "Make sure the testing was successful.");
      assertEquals(
          "1..1\nok 1 Phyloreference '1'\n# The following nodes were matched and expected this phyloreference: [1]\n\n",
          outputStr);
      resetIO();

      // Run 'test dummy1.txt' with the '--jsonld' option and see if we get the correct response.
      exitCode =
          jphyloref.execute(
              new String[] {"test", "src/test/resources/phylorefs/dummy1.txt", "--jsonld"});

      try {
        outputStr = output.toString("UTF-8");
        errorStr = error.toString("UTF-8");
      } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      }

      assertEquals(0, exitCode);
      assertTrue(
          errorStr.contains("Input: src/test/resources/phylorefs/dummy1.txt"),
          "Make sure JPhyloRef reports the name of the file being processed");
      assertTrue(
          errorStr.endsWith(
              "Testing complete:1 successes, 0 failures, 0 failures marked TODO, 0 skipped.\n"),
          "Make sure the testing was successful.");
      assertEquals(
          "1..1\nok 1 Phyloreference '1'\n# The following nodes were matched and expected this phyloreference: [1]\n\n",
          outputStr);
      resetIO();
    }
  }
}
