# Change log for JPhyloref

Based on the suggestion at https://keepachangelog.com/en/1.0.0/.

## [Unreleased]
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

## 0.2 - 2018-06-20
- Added support for phyloreference statuses using the Publication Status Ontology
  as per [phyloref/curation-tool#25].
- Added a `--no-reasoner` mode to testing, allowing pre-reasoned ontologies to be
  tested.
- Fixed some spacing and line-ending issues.

## 0.1 - 2018-06-20
- Initial release, with support for testing phyloreferences expressed in OWL
  and stored in RDF/XML.

[Unreleased]: https://github.com/phyloref/jphyloref/compare/v0.2...HEAD
[phyloref/curation-tool#25]: https://github.com/phyloref/curation-tool/issues/25
[phyloref/jphyloref#13]: https://github.com/phyloref/jphyloref/pull/13
[phyloref/jphyloref#12]: https://github.com/phyloref/jphyloref/pull/12
[phyloref/jphyloref#8]: https://github.com/phyloref/jphyloref/issues/8
