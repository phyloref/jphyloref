# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and test

```console
mvn package                     # builds target/JPhyloRef.jar (shaded, executable)
mvn test                        # unit tests + Spotless check + JaCoCo coverage check
mvn spotless:apply              # fix formatting (google-java-format GOOGLE style)
mvn test -Dtest=TestCommandTest     # single test class, including its @Nested classes
```

- Every test method lives inside a JUnit `@Nested` inner class. Surefire 3 runs a class's nested classes when `-Dtest` names the outer one; Surefire 2.22 did not, and needed `-Dtest='TestCommandTest*'`.
- **Keep JUnit current.** For release 18 and later, javac leaves out an inner class's `this$0` field when the class never uses its enclosing instance. JUnit 5.3 found the outer test instance through that field, so moving the target from Java 8 to 21 broke `@BeforeEach` in the outer class for such nested classes ("Failed to find instance for method"). JUnit 5.4+ tracks test instances itself.
- Spotless runs in the `test` phase and **fails the build** on unformatted Java, XML, Markdown, or JSON. Run `mvn spotless:apply` before committing. The tree is currently Spotless-clean, so a run that reports no changes is the expected result. Note that `spotless:apply` rewrites file mtimes even when content is unchanged, which makes `git status` list files as modified while `git diff` shows nothing — trust `git diff`.
- JaCoCo's coverage `check` has `haltOnFailure=false`, so coverage shortfalls only show up in the log.
- JPhyloRef targets Java 21 (`maven.compiler.release` in pom.xml): the oldest LTS that Eclipse Temurin supports beyond October 2027, when 11 and 17 reach end of support. CI builds on every LTS from the target upwards, currently JDK 21 and 25. Add each new LTS to the matrix as soon as Temurin ships it, so breakage shows up while the older LTSes are still supported; newer JDKs have broken the build tooling before. Raising the target is a deliberate decision, made against Temurin's support dates at https://adoptium.net/support/. The Dockerfile builds and runs on Temurin 25, so keep its JDK in the matrix.
- The shaded JAR is always `target/JPhyloRef.jar` (`<finalName>`), not a versioned filename.

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

- `JPhyloRef.VERSION` is read at class-load time from `src/main/resources/jphyloref.properties`, which Maven filters with `${project.version}`. Bump the version in pom.xml only; the properties file needs no edit. It falls back to `"(unknown)"` if the resource is missing. CHANGELOG.md still needs updating by hand on release.
- Tests in `TestCommandTest`/`ResolveCommandTest` assert against **exact stdout strings**, including the reasoner version line (`# Using reasoner: ELK/2016-01-11T13:41:15Z`). Any change to TAP output, log wording, or the ELK dependency version breaks them.
- `ReasonerHelper.getReasonerNameAndVersion()` parses ELK's version out of a `NumberFormatException` message — ELK throws when asked for its own version. Not a bug to "fix".
- **Dependency versions are coupled in two places**, both commented in pom.xml. `rdf4j.version` must match the rdf4j that `owlapi-rio` is built against, or two rdf4j releases end up in the shaded JAR. `slf4j-simple` must share a major version with `slf4j-api` (which OWLAPI pulls in): slf4j-api 2.x silently ignores a 1.7.x binding and discards all logging, which shows up only as `AppTest`/`TestCommandTest` failures on empty stderr. The `<dependencyManagement>` block pins transitive libraries past vulnerable versions; recheck each pin when upgrading the dependency that brings it in.
- Test fixtures live in `src/test/resources/phylorefs/`; `dummy1.*` resolve correctly, `failing1.*` exercise failure/skip paths.
- **Every JSON-LD test needs network access.** The fixtures reference `http://www.phyloref.org/phyx.js/context/v0.2.0/phyx.json` and jsonld-java fetches it at parse time; offline, behind a firewall, or if that URL stops resolving, those tests die with `JsonLdError: loading remote context failed`. The `.owl` fixtures are unaffected. This has taken the build red before, and it hits users too — `resolve`/`test` on any `.jsonld` file and the webserver's `/reason` endpoint fetch the same context. The unmerged `quick-fix-context-download` branch sketches the fix: bundle the contexts under `src/main/resources/context/` and register them with a `DocumentLoader`'s `addInjectedDoc()`.

## Deployment

`Dockerfile` is a multi-stage build producing an image that runs [webhook](https://github.com/adnanh/webhook) (not the built-in `webserver` command) invoking `webhook/exec_jphyloref.sh`, which calls `resolve ... -j --errors-as-json`. webhook is compiled from source with a current Go rather than installed from apt, because Ubuntu's package carries an outdated, statically linked Go standard library that vulnerability scanners flag and `apt-get upgrade` cannot fix; the Dockerfile's header comment explains where scan findings come from and how each kind is fixed. `webhook/exec_jphyloref_srun.sh` is the SLURM variant. See `webhook/README.md`.

`webhook/klados-backend.container` is a Podman Quadlet with `AutoUpdate=registry` on `:latest`, so a deployment picks up a new image only when one is pushed under that tag. Images are only built by `.github/workflows/docker.yaml`, on a published release (tagged `X.Y.Z`, `X.Y` and `latest`) or by hand with `workflow_dispatch` on a release tag; an image that is never rebuilt never gets OS or Go security fixes.

To smoke-test a built image the way the deployment calls it, POST `{"jsonldGzipped": "<base64 of a gzipped .jsonld>"}` to `http://localhost:8080/reason` (`start.sh` sets an empty URL prefix, so it is not `/hooks/reason`) with `X-Hub-Signature: sha1=<HMAC-SHA1 of the body>`; `hooks.json` uses the secret `undefined`. If you mount a tmpfs on `/data`, it must be writable by the image's `nru` user (uid 1001).

Publishing to Sonatype OSSRH is `mvn clean deploy` and requires GPG settings in `~/.m2/settings.xml`.
