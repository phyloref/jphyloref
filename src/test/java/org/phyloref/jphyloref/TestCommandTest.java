package org.phyloref.jphyloref;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

  /**
   * Helper method for testing whether analysing a single phyloreference with a particular name
   * succeeded.
   */
  private void expectSinglePhylorefResolvingCorrectly(
      String filename,
      String phylorefName,
      String resolvedNode,
      String stdoutStr,
      String stderrStr) {
    assertTrue(
        stderrStr.contains("Input: " + filename),
        "Make sure JPhyloRef reports the name of the file being processed");
    assertTrue(
        stderrStr.endsWith(
            "Testing complete:1 successes, 0 failures, 0 failures marked TODO, 0 skipped.\n"),
        "Stderr should end with single success but returned: " + stderrStr);
    assertEquals(
        "1..1\n# From file: "
            + filename
            + "\n# Using reasoner: ELK/2016-01-11T13:41:15Z\nok 1 "
            + phylorefName
            + "\n# Expected nodes: ["
            + resolvedNode
            + "]\n"
            + "# Resolved nodes: ["
            + resolvedNode
            + "]\n"
            + "\n",
        stdoutStr);
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
      expectSinglePhylorefResolvingCorrectly(
          "src/test/resources/phylorefs/dummy1.owl",
          "Phyloreference '1'",
          "#phylogeny0_node2",
          outputStr,
          errorStr);
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
      expectSinglePhylorefResolvingCorrectly(
          "src/test/resources/phylorefs/dummy1.jsonld",
          "Phyloreference '1'",
          "#phylogeny0_node2",
          outputStr,
          errorStr);
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
      expectSinglePhylorefResolvingCorrectly(
          "src/test/resources/phylorefs/dummy1.txt",
          "Phyloreference '1'",
          "#phylogeny0_node2",
          outputStr,
          errorStr);
      resetIO();
    }

    @Test
    @DisplayName("can load JSON-LD files from STDIN")
    void testJSONLDFromStdin() {
      // The test file we're going to use in this test.
      String testFilename = "src/test/resources/phylorefs/dummy1.jsonld";

      // We're going to take over STDIN, so let's save the real STDIN so we can
      // restore it.
      InputStream actualStdin = System.in;

      try {
        // Reset STDOUT and STDERR and set STDIN.
        resetIO();

        // Create an InputStream with the contents of the JSON-LD file we've
        // already tested, and set the System STDIN to point to that input stream.
        ByteArrayInputStream input =
            new ByteArrayInputStream(Files.readAllBytes(Paths.get(testFilename)));
        System.setIn(input);

        // Test the contents of STDIN.
        int exitCode = jphyloref.execute(new String[] {"test", "--jsonld", "-"});

        // Obtain STDOUT and STDERR.
        String outputStr = output.toString("UTF-8");
        String errorStr = error.toString("UTF-8");

        // Test whether the exit code, STDERR and STDOUT is as expected.
        assertEquals(0, exitCode);
        expectSinglePhylorefResolvingCorrectly(
            "-", "Phyloreference '1'", "#phylogeny0_node2", outputStr, errorStr);
      } catch (UnsupportedEncodingException ex) {
        // Could be thrown when converting STDOUT/STDERR to String as UTF-8.
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      } catch (IOException ex) {
        // Could be thrown if there were errors reading the testing file.
        throw new RuntimeException(
            "Expected testing file '" + testFilename + "' not found or could not be read: " + ex);
      } finally {
        // Reset System.in to the actual STDIN.
        System.setIn(actualStdin);

        // Reset everything else as well.
        resetIO();
      }
    }

    @Test
    @DisplayName(
        "can report an ontology with one correctly resolved and one incorrectly resolved phyloref")
    void testJSONLDWithFailure() {
      // The test file we're going to use in this test.
      String testFilename = "src/test/resources/phylorefs/failing1.jsonld";

      // We're going to take over STDIN, so let's save the real STDIN so we can
      // restore it.
      InputStream actualStdin = System.in;

      try {
        // Reset STDOUT and STDERR and set STDIN.
        resetIO();

        // Test the test file, which we expect to have one success and one failure.
        int exitCode = jphyloref.execute(new String[] {"test", "--jsonld", testFilename});

        // Obtain STDOUT and STDERR.
        String outputStr = output.toString("UTF-8");
        String errorStr = error.toString("UTF-8");

        // Test whether the exit code, STDERR and STDOUT is as expected.
        assertEquals(1, exitCode);
        assertTrue(
            errorStr.contains("Input: " + testFilename),
            "Make sure JPhyloRef reports the name of the file being processed");
        assertTrue(
            errorStr.endsWith(
                "Testing complete:1 successes, 1 failures, 0 failures marked TODO, 1 skipped.\n"),
            "Stderr should end with single success but returned: " + errorStr);
        assertEquals(
            "1..3\n# From file: "
                + testFilename
                + "\n# Using reasoner: ELK/2016-01-11T13:41:15Z\n"
                + "ok 1 Phyloreference '1'\n"
                + "# Expected nodes: [#phylogeny0_node2]\n"
                + "# Resolved nodes: [#phylogeny0_node2]\n"
                + "not ok 2 Phyloreference '2'\n"
                + "# Expected nodes: [#phylogeny0_node1]\n"
                + "# Resolved nodes: [#phylogeny0_node2]\n"
                + "# Some nodes were resolved but were not expected: [<http://example.org/jphyloref#phylogeny0_node2>]\n"
                + "# Some nodes were expected but were not resolved: [<http://example.org/jphyloref#phylogeny0_node1>]\n"
                + "not ok 3 Phyloreference '4' # SKIP Phyloreference has no expected resolution, and so cannot be tested.\n"
                + "# Expected nodes: []\n"
                + "# It resolved to the following 1 nodes: [<http://example.org/jphyloref#phylogeny0_node2>]\n"
                + "\n",
            outputStr);

      } catch (UnsupportedEncodingException ex) {
        // Could be thrown when converting STDOUT/STDERR to String as UTF-8.
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      } catch (IOException ex) {
        // Could be thrown if there were errors reading the testing file.
        throw new RuntimeException(
            "Expected testing file '" + testFilename + "' not found or could not be read: " + ex);
      } finally {
        // Reset STDOUT and STDERR.
        resetIO();
      }
    }
  }
}
