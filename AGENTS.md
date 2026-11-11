# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and test

```console
mvn package                     # builds target/JPhyloRef.jar (shaded, executable)
mvn test                        # unit tests + Spotless check + JaCoCo coverage check
mvn spotless:apply              # fix formatting (google-java-format GOOGLE style)
mvn test -Dtest='TestCommandTest*'   # single test class — the trailing * is required
```

- **The trailing `*` in `-Dtest` is not optional.** Every test method lives inside a JUnit 5 `@Nested` inner class, so `-Dtest=TestCommandTest` matches nothing and Surefire 2.22.1 fails with "No tests were executed!".
- **JaCoCo 0.8.6 cannot instrument class files newer than Java 15**, so `mvn test` fails on a modern JDK with `Unsupported class file major version NN` before any test runs. Either build under a JDK ≤ 15 (CI uses 8) or add `-Djacoco.skip=true` locally.
- Spotless runs in the `test` phase and **fails the build** on any file it would change. Java is checked against google-java-format; XML, Markdown and JSON are only checked for whitespace (trailing whitespace, a final newline, tabs instead of two-space indents), so Spotless says nothing about their structure or formatting. Run `mvn spotless:apply` before committing. It rewrites file mtimes even when content is unchanged, which makes `git status` list files as modified while `git diff` shows nothing — trust `git diff`.
- JaCoCo's coverage `check` has `haltOnFailure=false`, so coverage shortfalls only show up in the log.
- Source/target is Java 8 (CI builds on JDK 8); the Dockerfile builds and runs on Temurin 21.
- The shaded JAR is always `target/JPhyloRef.jar` (`<finalName>`), never a versioned filename.

Run the built JAR:

```console
java -jar target/JPhyloRef.jar test src/test/resources/phylorefs/dummy1.owl
java -jar target/JPhyloRef.jar resolve src/test/resources/phylorefs/failing1.jsonld -j
java -jar target/JPhyloRef.jar webserver --host 0.0.0.0 --port 8080
```

## Architecture

JPhyloRef reads an OWL ontology containing phyloreferences (clade definitions as OWL classes) plus reference phylogenies (nodes as OWL individuals), runs an OWL reasoner over it, and reports which nodes each phyloreference resolved to.

**Command dispatch.** `JPhyloRef.execute(String[])` builds a `commands` list (`HelpCommand`, `TestCommand`, `WebserverCommand`, `ResolveCommand`), collects command-line options from each `Command.addCommandLineOptions()`, parses with commons-cli, then dispatches on the first positional argument. `main()` just wraps this and calls `System.exit()`. Adding a command means implementing `commands/Command` and adding it to that list — nothing else registers it.

**The three commands share one pipeline.** Loading is duplicated in `TestCommand`, `ResolveCommand`, and `WebserverCommand.Webserver.serveReason()`, so a change there usually needs making in all three. Reasoner creation (`ReasonerHelper.createReasonerFromCmdLine()`) is shared, and so is resolution for `resolve` and `/reason` (`PhylorefHelper.resolvePhyloreferences()`). Each command disposes of its reasoner in a `finally`, since the webserver is long-running and ELK keeps worker threads until disposed.

1. Load the ontology: RDF/XML via `OWLManager.loadOntologyFromOntologyDocument()`, or JSON-LD via `JSONLDHelper.createRDFParserForOntology()` (an rdf4j Rio parser feeding OWL API's `RioOWLRDFConsumerAdapter`). In `test` and `resolve`, JSON-LD is used when `--jsonld`/`-j` is given or the filename ends in `.json`/`.jsonld`. The webserver's `/reason` **always** parses JSON-LD and cannot take RDF/XML. JSON-LD input gets the base URI `http://example.org/jphyloref`.
2. An `AutoIRIMapper` over a local `ontologies/` directory lets imports resolve offline when purl.obolibrary.org is unreachable.
3. `ReasonerHelper.createReasonerFromCmdLine()` creates the reasoner named by the `--reasoner` global option (`elk` by default). `--reasoner null` gives a null reasoner: `resolve` and `webserver` then skip reasoning, while `test` refuses to run (exit code 1), because it needs a reasoner to compute expected nodes (below).
4. `PhylorefHelper.getPhyloreferences()` finds phyloreference classes, minus the ontology's own `Phyloreference*` metaclasses: with a reasoner, all direct *and* indirect subclasses of `phyloref:Phyloreference`, minus unsatisfiable classes; without one, only named classes asserted as direct subclasses. Both throw `IllegalArgumentException` if the ontology doesn't define `phyloref:Phyloreference` exactly once.
5. `PhylorefHelper.getNodesInClass()` returns the nodes each phyloref resolved to: the reasoner's instances, or, without a reasoner, individuals with a class assertion of exactly that phyloref. `resolve --reasoner null` therefore reports the resolutions recorded in a pre-reasoned ontology. Assertions that merely *mention* the phyloref don't count, and that includes the expected-node assertions below: an ontology that hasn't been reasoned over resolves to nothing without a reasoner.

**Expected vs. resolved (`TestCommand`).** Expected nodes are not a plain annotation: they are the instances of the class expression `OBI:is_specified_output_of some (OBI:has_specified_input some <phyloref>)` (Model 2.0 semantics, since v0.4.0). A phyloref with no expected nodes is reported as SKIP. `PhylorefHelper.getStatusesForPhyloref()` reads pso/tvc/timeinterval annotations to decide whether a phyloref is *expected* to resolve. A status is *active* if it has a start date and no end date. A phyloref with no active statuses is expected to resolve; one with active statuses is expected to resolve only if at least one of them is `pso:submitted` or `pso:published`. Failures of phylorefs not expected to resolve are marked TODO rather than counted as failures.

Exit code: `-1` if no phyloref succeeded, otherwise the number of failures (so `0` is full success); `1` if the ontology could not be loaded or `--reasoner null` was given. `-1` is returned even when nothing failed, e.g. when every phyloref was SKIP or TODO, or when there were none. The shell sees the code modulo 256: `-1` arrives as `255`, and 256 failures arrive as `0`.

**Output surfaces.**

- `test` writes TAP 13 (tap4j) to stdout and a summary to stderr.
- `resolve` writes `{"phylorefs": {<phyloref>: [<node>, ...]}}` to stdout. The `http://example.org/jphyloref` base URI is stripped from the start of both phyloref and node IRIs, so phylorefs in a JSON-LD file with no base of its own come out as relative identifiers like `#phyloref0`.
- `resolve --errors-as-json` prints errors to stdout as `{"error": ..., "message": ...}` instead of to stderr, and **then exits 0 whatever went wrong**. The webhook deployment relies on this, and `webhook/exec_jphyloref.sh` also discards stderr, so callers must check for an `error` key; the exit status will not tell them.
- `webserver` (NanoHTTPD) serves `GET /version` and `POST /reason`. `/reason` takes JSON-LD in one of three form fields: `jsonld` (a string), `jsonldGzipped` (Base64-encoded gzip of the string) or `jsonldFile` (a file upload). It responds with `status`, `ontology` and the same `phylorefs` map as `resolve`. Errors are JSON with `status: "error"` and an `error` message: 400 for a missing form field or an ontology without phylorefs, 500 otherwise.

## Gotchas

- `JPhyloRef.VERSION` is read at class-load time from `src/main/resources/jphyloref.properties`, which Maven filters with `${project.version}`, so `help` and `/version` report the version in pom.xml. It falls back to `"(unknown)"` if the resource is missing or unfiltered (an IDE build that skips Maven's resource filtering), which `AppTest` catches. On release, bump `<version>` in pom.xml (the properties file needs no edit), and update by hand `version`, `date-released` and the versioned DOI in CITATION.cff, and CHANGELOG.md.
- Tests in `TestCommandTest`/`ResolveCommandTest` assert against **exact stdout strings**, including the reasoner version line (`# Using reasoner: ELK/2016-01-11T13:41:15Z`). Any change to TAP output, log wording, or the ELK dependency version breaks them.
- `ReasonerHelper.getReasonerNameAndVersion()` parses ELK's version out of a `NumberFormatException` message — ELK throws when asked for its own version. Not a bug to "fix".
- Test fixtures live in `src/test/resources/phylorefs/`. `dummy1.*` resolve correctly with a reasoner. `failing1.*` exercise failure and skip paths. `prereasoned.jsonld` asserts its resolution, for testing `--reasoner null`. `no-phyloref-class.jsonld` lacks `phyloref:Phyloreference`, for testing error reporting. The last two use an inline `@context` and need no network. `WebserverCommandTest` drives `/reason` over real HTTP on an ephemeral port.
- **JSON-LD tests on the Phyx fixtures need network access.** `dummy1.jsonld` and `failing1.jsonld` reference `http://www.phyloref.org/phyx.js/context/v0.2.0/phyx.json` and jsonld-java fetches it at parse time; offline, behind a firewall, or if that URL stops resolving, those tests die with `JsonLdError: loading remote context failed`. The `.owl` fixtures are unaffected. This has taken the build red before, and it hits users too — `resolve`/`test` on any `.jsonld` file and the webserver's `/reason` endpoint fetch the same context. The unmerged `quick-fix-context-download` branch sketches the fix: bundle the contexts under `src/main/resources/context/` and register them with a `DocumentLoader`'s `addInjectedDoc()`.

## Deployment

`Dockerfile` is a two-stage build producing an image that runs [webhook](https://github.com/adnanh/webhook) (not the built-in `webserver` command) invoking `webhook/exec_jphyloref.sh`, which calls `resolve ... -j --errors-as-json`. `webhook/exec_jphyloref_srun.sh` is the SLURM variant. See `webhook/README.md`.

Publishing to Sonatype OSSRH is `mvn clean deploy` and requires GPG settings in `~/.m2/settings.xml`.
