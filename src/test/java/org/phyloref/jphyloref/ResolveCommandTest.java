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

/** A unit test for the ResolveCommand class */
@DisplayName("ResolveCommandTest")
class ResolveCommandTest {
  static JPhyloRef jphyloref = new JPhyloRef();
  static ByteArrayOutputStream output = new ByteArrayOutputStream();
  static ByteArrayOutputStream error = new ByteArrayOutputStream();

  private static final String EXPECTED_DUMMY1_RESOLUTION =
      "{\"phylorefs\":{\"#phyloref0\":[\"#phylogeny0_node2\"]}}\n";
  private static final String EXPECTED_DUMMY1_RESOLUTION_RDF =
      "{\"phylorefs\":{\"file:///Users/gaurav/Development/phyloref/jphyloref/src/test/resources/phylorefs/dummy1.jsonld#phyloref0\":[\"file:///Users/gaurav/Development/phyloref/jphyloref/src/test/resources/phylorefs/dummy1.jsonld#phylogeny0_node2\"]}}\n";

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
  @DisplayName("can resolve OWL files")
  class TestingOWLFile {
    @Test
    @DisplayName("successfully resolves phylorefs in OWL files")
    void resolveOWLFiles() {
      // Run 'test dummy1.owl' and see if we get the correct response.
      int exitCode =
          jphyloref.execute(new String[] {"resolve", "src/test/resources/phylorefs/dummy1.owl"});

      String outputStr, errorStr;
      try {
        outputStr = output.toString("UTF-8");
        errorStr = error.toString("UTF-8");
      } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      }

      assertEquals(0, exitCode);
      assertEquals(EXPECTED_DUMMY1_RESOLUTION_RDF, outputStr);
    }
  }

  @Nested
  @DisplayName("can resolve phylorefs in JSON-LD files")
  class TestingJSONLDFile {
    @Test
    @DisplayName("successfully resolves phylorefs in JSON-LD files")
    void resolveJSONLDFiles() {
      // Run 'resolve dummy1.jsonld' and see if we get the correct response.
      int exitCode =
          jphyloref.execute(new String[] {"resolve", "src/test/resources/phylorefs/dummy1.jsonld"});

      String outputStr, errorStr;
      try {
        outputStr = output.toString("UTF-8");
        errorStr = error.toString("UTF-8");
      } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      }

      assertEquals(0, exitCode);
      assertEquals(EXPECTED_DUMMY1_RESOLUTION, outputStr);
      resetIO();

      // Run 'resolve dummy1.txt' with the '--jsonld' option and see if we get the correct response.
      exitCode =
          jphyloref.execute(
              new String[] {"resolve", "src/test/resources/phylorefs/dummy1.txt", "--jsonld"});

      try {
        outputStr = output.toString("UTF-8");
        errorStr = error.toString("UTF-8");
      } catch (UnsupportedEncodingException ex) {
        throw new RuntimeException("'UTF-8' is not supported as an encoding: " + ex);
      }

      assertEquals(0, exitCode);
      assertEquals(EXPECTED_DUMMY1_RESOLUTION, outputStr);
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
        int exitCode = jphyloref.execute(new String[] {"resolve", "--jsonld", "-"});

        // Obtain STDOUT and STDERR.
        String outputStr = output.toString("UTF-8");
        String errorStr = error.toString("UTF-8");

        // Test whether the exit code, STDERR and STDOUT is as expected.
        assertEquals(0, exitCode);
        assertEquals(EXPECTED_DUMMY1_RESOLUTION, outputStr);
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
  }
}
