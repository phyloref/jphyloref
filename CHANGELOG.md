# Change log for JPhyloref

Based on the suggestion at https://keepachangelog.com/en/1.0.0/.

## [Unreleased]
- Fixed `--reasoner null` crashing the `resolve` command and the webserver; it now skips
  reasoning and reports the resolutions asserted in the ontology. The `test` command, which
  needs a reasoner to determine expected nodes, now rejects it with an error message.
- Without a reasoner, a node now counts as resolved only if it is asserted to be a member of
  the phyloreference itself. Previously any class assertion mentioning the phyloreference
  counted, including the assertion of which node it is *expected* to resolve to. Anonymous
  subclasses of phyloref:Phyloreference and the Phyloref ontology's own metaclasses are no
  longer reported as phyloreferences.
- The webserver's `/reason` endpoint now answers every error with JSON (400 for an ontology
  without phyloreferences) instead of letting some exceptions escape, and always disposes of
  its reasoner, which previously leaked after a failed request.
- `resolve --errors-as-json` now reports an input file that cannot be opened as a JSON error,
  like every other error, instead of exiting 1 with nothing on stdout.
- `JPhyloRef.VERSION` is now read from a Maven-filtered properties file instead of
  being hardcoded, so `help` and the webserver's `/version` endpoint report the
  version declared in pom.xml.
- JPhyloRef now requires Java 21 or later (previously Java 8). The tests now run on
  JUnit 6.1.3 with Surefire 3.6.1.
- Upgraded JaCoCo to 0.8.13 so that `mvn test` works on JDKs newer than 15.
- Upgraded org.json to 20260814 and the Spotless plugin to 1.20.0 to resolve four
  Dependabot advisories (three high-severity, one medium).
- Upgraded OWLAPI to 4.5.29, rdf4j to 3.7.7, tap4j to 4.4.2, commons-cli to 1.11.0
  and slf4j to 2.0.20, replaced log4j 1.2.14 (from ELK) with reload4j 1.2.27, removed
  the unused Sesame 1.x `openrdf-model` dependency, and pinned newer Jackson,
  commons-io, Guava and SnakeYAML, so that no library in the JAR has a known advisory.
- The Docker image now runs on Temurin 25 (Ubuntu 26.04) with security updates applied
  at build time, and builds webhook from source with a current Go instead of using
  Ubuntu's package.
- The Docker image now runs a thin JPhyloRef.jar with its dependencies in `/app/lib`, built
  by a new `docker` Maven profile, instead of the shaded JAR.
- The shaded JAR now registers every OWLAPI and rdf4j parser and writer; previously only
  the first of several same-named service files survived shading.
- The Maven CI workflow now builds on every Java LTS from 21 upwards (currently 21 and 25)
  rather than JDK 8 alone, smoke-tests the shaded JAR, and builds and smoke-tests the
  Docker image.

## [1.1.1] - 2021-08-10
- Fixed manuscript for submission to the Journal of Open Source Software (JOSS).

## [1.1.0] - 2021-08-07
- Prepared manuscript for submission to the Journal of Open Source Software (JOSS).
- Improved documentation based on JOSS reviewer feedback.
- Improved pom.xml based on JOSS reviewer feedback.
- Removed JFact reasoner as it only works on Java 15 and earlier versions of Java.
  This allows us to support Java 16.

## [1.0.0] - 2021-04-15
- Improved README by adding badges and build and execution instructions.
- Removed FaCT++ JNI reasoner (JFact is still included.)

## [0.4.0] - 2021-02-03
- Updated JPhyloRef to use model 2.0 expectations, encoded as logical expressions
  on the phylogeny node, rather than comparing labels or using text-based properties.
  You will need to use Phyx.js 0.2.0 or higher to generate ontologies that can be
  tested with this version of JPhyloRef.
- Phyloreferences without any expected resolution are now skipped during testing.
- Replaced Travis CI as continuous integration system with Github Actions.

## [0.3.1] - 2020-05-13
- JPhyloRef was incorrectly returning an exit code of `0` whether or not
  testing succeeded. It now returns `0` only if all testing succeeded,
  `-1` if no phyloreferences succeeded, and the number of failing
  phyloreferences otherwise.

## [0.3] - 2020-04-20
- Replaced `System.err` and `System.out` with calls to SLF4J (#51).
- Display the filename of the input file in the testing output.
- Added a "resolve" command that resolves phylorefs in input ontologies (in OWL
  or JSON-LD) and returns the result as a JSON string (#52).
- Centralized code for determining nodes in phylorefs (#53, #54).
- Added support for the Elk reasoner (as "elk") and made it the default reasoner.
- Phyloreferences were previously identified in input ontologies by looking for
  individuals defined as instances of the class phyloref:Phyloreference. They
  are now identified as subclasses of phyloref:Phyloreference. This is in line
  with the changes introduced by moving from the 2018-12-04 release of the
  Phyloref Ontology to the 2018-12-14 release.
- Removed ReasonCommand, which is no longer useful.
- Updated dependency to JFact 5.0.1, which necessitated some code changes.
- Added a "webserver" command that starts a webserver that provides a simple
  HTTP API for reasoning over JSON-LD file in [phyloref/jphyloref#12].
- Removed Eclipse files that should not have been added in [phyloref/jphyloref#13].
- Moved code for determining Phyloref statuses to PhylorefHelper. This is part of
  [phyloref/jphyloref#8].
- Added support for JSON-LD files without a base URI by using a temporary URI
  internally.
- WebserverCommand now looks for ontologies in the local 'ontologies/' directory.

## [0.2] - 2018-06-20
- Added support for phyloreference statuses using the Publication Status Ontology
  as per [phyloref/curation-tool#25].
- Added a `--no-reasoner` mode to testing, allowing pre-reasoned ontologies to be
  tested.
- Fixed some spacing and line-ending issues.

## [0.1] - 2018-06-20
- Initial release, with support for testing phyloreferences expressed in OWL
  and stored in RDF/XML.

[Unreleased]: https://github.com/phyloref/jphyloref/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/phyloref/jphyloref/releases/tag/v1.1.1
[1.1.0]: https://github.com/phyloref/jphyloref/releases/tag/v1.1.0
[1.0.0]: https://github.com/phyloref/jphyloref/releases/tag/v1.0.0
[0.4.0]: https://github.com/phyloref/jphyloref/releases/tag/v0.4.0
[0.3.1]: https://github.com/phyloref/jphyloref/releases/tag/v0.3.1
[0.3]: https://github.com/phyloref/jphyloref/releases/tag/v0.3
[0.2]: https://github.com/phyloref/jphyloref/releases/tag/v0.2
[0.1]: https://github.com/phyloref/jphyloref/releases/tag/v0.1

[phyloref/curation-tool#25]: https://github.com/phyloref/curation-tool/issues/25
[phyloref/jphyloref#13]: https://github.com/phyloref/jphyloref/pull/13
[phyloref/jphyloref#12]: https://github.com/phyloref/jphyloref/pull/12
[phyloref/jphyloref#8]: https://github.com/phyloref/jphyloref/issues/8
