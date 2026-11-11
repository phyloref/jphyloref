package org.phyloref.jphyloref.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.phyloref.jphyloref.helpers.ReasonerHelper;

/** A unit test for the webserver's /reason endpoint, run over real HTTP on an ephemeral port. */
@DisplayName("WebserverCommandTest")
class WebserverCommandTest {
  /** Start a webserver with the given reasoner on a free port. Callers must stop() it. */
  private static WebserverCommand.Webserver startWebserver(String reasoner)
      throws IOException, ParseException {
    Options opts = new Options();
    ReasonerHelper.addCommandLineOptions(opts);
    CommandLine cmdLine = new DefaultParser().parse(opts, new String[] {"--reasoner", reasoner});

    WebserverCommand cmd = new WebserverCommand();
    return cmd.new Webserver(cmd, "localhost", 0, cmdLine);
  }

  /** POST a JSON-LD file to /reason as the 'jsonld' form field. */
  private static Response postReason(WebserverCommand.Webserver webserver, String filename)
      throws IOException {
    String jsonld = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
    return postForm(webserver, "jsonld", jsonld);
  }

  /** POST a single form field to /reason. */
  private static Response postForm(WebserverCommand.Webserver webserver, String field, String value)
      throws IOException {
    byte[] body =
        (field + "=" + URLEncoder.encode(value, "UTF-8")).getBytes(StandardCharsets.UTF_8);

    URL url = new URL("http://localhost:" + webserver.getListeningPort() + "/reason");
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    try (OutputStream os = conn.getOutputStream()) {
      os.write(body);
    }

    int status = conn.getResponseCode();
    try (InputStream is = (status < 400) ? conn.getInputStream() : conn.getErrorStream();
        Scanner scanner = new Scanner(is, "UTF-8")) {
      return new Response(status, scanner.useDelimiter("\\A").next());
    }
  }

  private static class Response {
    final int status;
    final String body;

    Response(int status, String body) {
      this.status = status;
      this.body = body;
    }
  }

  @Nested
  @DisplayName("serves /reason that")
  class Reason {
    @Test
    @DisplayName("resolves phylorefs from asserted class assertions with '--reasoner null'")
    void resolvesWithoutReasoner() throws IOException, ParseException {
      WebserverCommand.Webserver webserver = startWebserver("null");
      try {
        Response response =
            postReason(webserver, "src/test/resources/phylorefs/prereasoned.jsonld");

        assertEquals(200, response.status, response.body);
        JSONObject phylorefs = new JSONObject(response.body).getJSONObject("phylorefs");
        assertEquals("[\"#node1\"]", phylorefs.getJSONArray("#phyloref0").toString());
      } finally {
        webserver.stop();
      }
    }

    @Test
    @DisplayName("reports an ontology without phyloref:Phyloreference as a JSON 400")
    void reportsMissingPhyloreferenceClass() throws IOException, ParseException {
      // Both the reasoned and the asserted-only paths must answer with JSON, not a crash.
      for (String reasoner : new String[] {"elk", "null"}) {
        WebserverCommand.Webserver webserver = startWebserver(reasoner);
        try {
          Response response =
              postReason(webserver, "src/test/resources/phylorefs/no-phyloref-class.jsonld");

          assertEquals(400, response.status, "With --reasoner " + reasoner + ": " + response.body);
          JSONObject result = new JSONObject(response.body);
          assertEquals("error", result.getString("status"));
          assertTrue(
              result.getString("error").contains("is not defined in ontology"),
              "Unexpected error with --reasoner " + reasoner + ": " + result);
        } finally {
          webserver.stop();
        }
      }
    }

    @Test
    @DisplayName("deletes the temporary file it writes a 'jsonld' field into")
    void deletesTemporaryFile() throws IOException, ParseException {
      File tmpdir = new File(System.getProperty("java.io.tmpdir"));
      FilenameFilter ours = (dir, name) -> name.startsWith("jphyloref");
      Set<String> before = new HashSet<>(Arrays.asList(tmpdir.list(ours)));

      WebserverCommand.Webserver webserver = startWebserver("elk");
      try {
        // One request that succeeds and one that fails; neither may leave a file behind.
        postReason(webserver, "src/test/resources/phylorefs/prereasoned.jsonld");
        postReason(webserver, "src/test/resources/phylorefs/no-phyloref-class.jsonld");
      } finally {
        webserver.stop();
      }

      Set<String> after = new HashSet<>(Arrays.asList(tmpdir.list(ours)));
      after.removeAll(before);
      assertEquals(Collections.emptySet(), after, "Temporary files left in " + tmpdir);
    }

    @Test
    @DisplayName("reports a 'jsonldGzipped' field that is not Base64 as a JSON 400")
    void reportsInvalidBase64() throws IOException, ParseException {
      WebserverCommand.Webserver webserver = startWebserver("elk");
      try {
        Response response = postForm(webserver, "jsonldGzipped", "not base64!");

        assertEquals(400, response.status, response.body);
        assertTrue(
            new JSONObject(response.body).getString("error").contains("not valid Base64"),
            response.body);
      } finally {
        webserver.stop();
      }
    }
  }

  @Test
  @DisplayName("refuses to start with an unknown reasoner")
  void refusesUnknownReasoner() {
    assertThrows(IllegalArgumentException.class, () -> startWebserver("Elk"));
  }
}
