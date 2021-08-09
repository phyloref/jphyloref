# Change log for JPhyloref

Based on the suggestion at https://keepachangelog.com/en/1.0.0/.

## [1.1.0] - 2021-08-07
- Prepared manuscript for submission to the Journal of Open Source Software (JOSS).
- Improved documentation based on JOSS reviewer feedback.
- Improved pom.xml based on JOSS reviewer feedback.
- Removed JFact reasoner as it no longer works with Java 16.

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

[Unreleased]: https://github.com/phyloref/jphyloref/compare/v1.1.0...HEAD
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
