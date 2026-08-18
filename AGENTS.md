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
- Spotless runs in the `test` phase and **fails the build** on unformatted Java, XML, Markdown, or JSON. Run `mvn spotless:apply` before committing. The tree is currently Spotless-clean, so a run that reports no changes is the expected result. Note that `spotless:apply` rewrites file mtimes even when content is unchanged, which makes `git status` list files as modified while `git diff` shows nothing — trust `git diff`.
- JaCoCo's coverage `check` has `haltOnFailure=false`, so coverage shortfalls only show up in the log.
- Source/target is Java 8 (CI builds on JDK 8); the Dockerfile builds and runs on Temurin 21.
- The shaded JAR is always `target/JPhyloRef.jar` (`<finalName>`), not a versioned filename — README.md and Usage.md still show older versioned names.

Run the built JAR:

```console
java -jar target/JPhyloRef.jar test src/test/resources/phylorefs/dummy1.owl
java -jar target/JPhyloRef.jar resolve src/test/resources/phylorefs/failing1.jsonld -j
java -jar target/JPhyloRef.jar webserver --host 0.0.0.0 --port 8080
```

## Architecture

JPhyloRef reads an OWL ontology containing phyloreferences (clade definitions as OWL classes) plus reference phylogenies (nodes as OWL individuals), runs an OWL reasoner over it, and reports which nodes each phyloreference resolved to.

**Command dispatch.** `JPhyloRef.execute(String[])` builds a `commands` list (`HelpCommand`, `TestCommand`, `WebserverCommand`, `ResolveCommand`), collects command-line options from each `Command.addCommandLineOptions()`, parses with commons-cli, then dispatches on the first positional argument. `main()` just wraps this and calls `System.exit()`. Adding a command means implementing `commands/Command` and adding it to that list — nothing else registers it.

**The three commands share one pipeline** (duplicated, not factored out) in `TestCommand`, `ResolveCommand`, and `WebserverCommand.Webserver`:

1. Load the ontology: RDF/XML via `OWLManager.loadOntologyFromOntologyDocument()`, or JSON-LD via `JSONLDHelper.createRDFParserForOntology()` (an rdf4j Rio parser feeding OWL API's `RioOWLRDFConsumerAdapter`). JSON-LD is used when `--jsonld`/`-j` is given or the filename ends in `.json`/`.jsonld`, and gets the base URI `http://example.org/jphyloref`.
2. An `AutoIRIMapper` over a local `ontologies/` directory lets imports resolve offline when purl.obolibrary.org is unreachable.
3. `ReasonerHelper.getReasonerFactoryFromCmdLine()` picks the reasoner (`elk` default, `null` for none) from the `--reasoner` global option.
4. `PhylorefHelper.getPhyloreferences()` finds phyloreference classes — with a reasoner, all direct *and* indirect subclasses of `phyloref:Phyloreference`, minus unsatisfiable classes and the ontology's own `Phyloreference*` metaclasses; without one, only asserted subclasses.
5. `PhylorefHelper.getNodesInClass()` returns the nodes each phyloref resolved to (reasoner instances, or asserted class assertions when reasoning is skipped — this is what allows testing a pre-reasoned ontology).

**Expected vs. resolved (`TestCommand`).** Expected nodes are not a plain annotation: they are the instances of the class expression `OBI:is_specified_output_of some (OBI:has_specified_input some <phyloref>)` (Model 2.0 semantics, since v0.4.0). A phyloref with no expected nodes is reported as SKIP. `PhylorefHelper.getStatusesForPhyloref()` reads pso/tvc/timeinterval annotations to decide whether a phyloref is *expected* to resolve — a phyloref whose only active status (start date, no end date) is neither `pso:submitted` nor `pso:published` has its failures marked TODO rather than counted as failures. Exit code: `0` on full success, `-1` if no phyloref succeeded, otherwise the failure count.

**Output surfaces.** `test` writes TAP 13 (tap4j) to stdout and a summary to stderr; `resolve` writes a JSON object mapping phyloref IRI → resolved node IRIs to stdout; `webserver` (NanoHTTPD) serves `POST /reason` (form field `jsonld` or file upload `jsonldFile`) and `GET /version`. `resolve --errors-as-json` folds errors into the JSON payload instead of stderr — this is what the webhook deployment relies on.

## Gotchas

- `JPhyloRef.VERSION` is a hardcoded string (currently `"1.0.0"`) independent of the `<version>` in pom.xml. Both need updating on release, along with CHANGELOG.md.
- Tests in `TestCommandTest`/`ResolveCommandTest` assert against **exact stdout strings**, including the reasoner version line (`# Using reasoner: ELK/2016-01-11T13:41:15Z`). Any change to TAP output, log wording, or the ELK dependency version breaks them.
- `ReasonerHelper.getReasonerNameAndVersion()` parses ELK's version out of a `NumberFormatException` message — ELK throws when asked for its own version. Not a bug to "fix".
- Test fixtures live in `src/test/resources/phylorefs/`; `dummy1.*` resolve correctly, `failing1.*` exercise failure/skip paths.
- **Every JSON-LD test needs network access.** The fixtures reference `http://www.phyloref.org/phyx.js/context/v0.2.0/phyx.json` and jsonld-java fetches it at parse time; offline, behind a firewall, or if that URL stops resolving, those tests die with `JsonLdError: loading remote context failed`. The `.owl` fixtures are unaffected. This has taken the build red before, and it hits users too — `resolve`/`test` on any `.jsonld` file and the webserver's `/reason` endpoint fetch the same context. The unmerged `quick-fix-context-download` branch sketches the fix: bundle the contexts under `src/main/resources/context/` and register them with a `DocumentLoader`'s `addInjectedDoc()`.

## Deployment

`Dockerfile` is a two-stage build producing an image that runs [webhook](https://github.com/adnanh/webhook) (not the built-in `webserver` command) invoking `webhook/exec_jphyloref.sh`, which calls `resolve ... -j --errors-as-json`. `webhook/exec_jphyloref_srun.sh` is the SLURM variant. See `webhook/README.md`.

Publishing to Sonatype OSSRH is `mvn clean deploy` and requires GPG settings in `~/.m2/settings.xml`.
