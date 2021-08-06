# JPhyloRef

[![Build Status](https://github.com/phyloref/jphyloref/workflows/Build%20with%20Maven/badge.svg)](https://github.com/phyloref/jphyloref/actions?query=workflow%3A%22Build+with+Maven%22)
[![Maven Central](https://img.shields.io/maven-central/v/org.phyloref/jphyloref.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.phyloref%22%20AND%20a:%22jphyloref%22)
[![javadoc](https://javadoc.io/badge2/org.phyloref/jphyloref/javadoc.svg)](https://javadoc.io/doc/org.phyloref/jphyloref)
[![DOI](https://zenodo.org/badge/104808310.svg)](https://zenodo.org/badge/latestdoi/104808310)

JPhyloRef is a [Java]-based command line tool as well as a web service for reasoning with OWL ontologies containing
phyloreferences and their accompanying reference phylogenetic trees.

Phyloreferences are phylogenetic clade definitions in the form of well-structured machine-readable data with
machine-interpretable semantics. Phylogenetic clade definitions define groups of organisms consisting of an ancestor and
all of its descendants ("[clades]") based on shared ancestry. In evolutionary biology, clades are a fundamental unit for
understanding evolution and describing biodiversity (see [de Queiroz, 2007]). Phylogenetic clade definitions therefore provide
the theoretical foundation for the semantics of taxon concepts to be defined and reproducibly resolved within a hypothesis
of evolutionary relationships, i.e., a phylogeny. The aim of [Phyloreferencing] is to structure and represent the semantics
of a phylogenetic clade definition and phylogenetic hypotheses using [ontologies] and formal logic expressions (in the
[Web Ontology Language] (OWL)) such that machines can unambigiuously and reproducibly retrieve the nodes in a tree that
match the semantics of the clade definition (if any). For more information on the motivation for phyloreferencing see
[Cellinese et al., preprint], and for more information on how phyloreferences are implemented, see [the JOSS manuscript]
included in this repository, or [phyx.js], a JavaScript library for creating phyloreferences using OWL ontologies.

JPhyloRef has two main goals:

1. The primary one is to facilitate automated testing that the semantics
of the logical definitions imply ("resolve to") the correct nodes in the reference tree as clade ancestors. This is key in
supporting quality control for the digitization of phylogenetic clade definitions from natural language text to a structured
machine-interpretable representation. It also verifies that one of the theoretical foundational premises of phyloreferences,
computational reproducibility, holds in practice.
2. The secondary goal is to enable integration with external tools that need
to obtain the clade ancestor node(s) resulting from a given ontology of phyloreferences and reference tree(s). When run as
part of an automated testing workflow, JPhyloRef reports test results in the cross-platform [Test Anything Protocol]
format. When used to find clade ancestor nodes implied by logical clade definitions, results are returned as a [JSON] object.
JPhyloRef uses the [OWL API] reference library for reading Web Ontology Language (OWL) ontologies, and for the actual ontology reasoning step it uses an external and configurable OWL reasoner.

# Usage

JPhyloRef wraps the [ELK reasoner] and provides three ways in which it
can be used to resolve phyloreferences:

- `java -jar jphyloref.jar resolve input.owl`: Resolves phyloreferences in `input.owl`
  and returns the nodes they resolve to in a JSON document.
- `java -jar jphyloref.jar webserver`: Starts a webserver that accepts ontologies
  for reasoning and provides the result as a JSON document.
    - Requests should be sent via POST to the `/reason` endpoint as an HTML
      form submission. The form can contain the ontology as a file upload in the
      `jsonldFile` element or as a string in the `jsonld` element. We will return
      a response in JSON with the results of the reasoning or with an error message.
    - You can also use the `/version` endpoint to test whether the software is
      working. It will report on the version of JPhyloRef, OWLAPI and reasoner
      being used.
    - Note that no content is served at `/`; you will need to use `/version` to
      test that the server is running.
- `java -jar jphyloref.jar test input.owl`: Test all the phyloreferences in
  `input.owl` by comparing their resolution with the expected resolution recorded
  in the file.

Detailed usage instructions are included in the [JPhyloRef Usage document]. Documentation of the source code is included
as [Javadoc] comments, which are also available online [at javadoc.io].

## Command line options

Many command line options can be used for all included commands:
- `--jsonld` or `-j` can be used to interpret the input file as a JSON-LD file
  rather than an RDF/XML file (resolve or test only).
- `--host [hostname]` or `-h` can be used to set the hostname that the webserver
  should listen on (webserver only).
- `--port [port number]` or `-p` can be used to set the port that the webserver
  should listen on (webserver only).
- `--reasoner [name]` can be used to set the reasoner to use. The following reasoners
  are supported:
  - [Elk 0.4.3](https://github.com/liveontologies/elk-reasoner) (`elk`) is an OWL 2 EL
    reasoner. Other reasoners for the OWL-EL profile may work but have not been tested. OWL-DL reasoners have been found to have insufficient performance.

# Community guidelines

Development of JPhyloRef takes place in [our GitHub repository]. This includes [an issue tracker] for reporting any bugs you find or requesting any features you need. We welcome any pull requests to add additional features, tests or documentation. All new pull requests are tested with a [continuous testing workflow].

# Build and execution instructions

You will need [Java] and [Apache Maven] to build the software from source. We recommend installing these
tools using a package manager, such as [Homebrew] on macOS. You can use [jEnv] instead if you want to install
multiple Java versions on the same computer. Installation should set up the `JAVA_HOME` environment
variable; if not, you will need to set it to point at the directory containing your Java installation.

Once you have downloaded the source code to your computer, you can compile and test the code by running `mvn test`.

JPhyloRef can be built from source by running `mvn package` from the root directory of this repository. This will create a JAR file in the `target/` directory, which can be executed by running,
for example:

```
$ java -jar target/jphyloref-1.1.0-SNAPSHOT.jar test src/test/resources/phylorefs/dummy1.owl
```

(Note that `1.1.0` should be replaced by the correct version number -- look for the `Building JPhyloRef N.M.K-SNAPSHOT` line in the output from `mvn package`, where N.M.K is the version, such as 1.1.0)

You can also download any published version of this software directly from Maven
at https://search.maven.org/artifact/org.phyloref/jphyloref.

If you have [Coursier] installed, you can download and run JPhyloRef in one step
by running:

```
$ coursier launch org.phyloref:jphyloref:1.0.0 -- test input.owl
```

# Hosting a server with Webhook

JPhyloRef can be [set up on a SLURM cluster using Webhook](webhook/README.md),
allowing jobs to be executed on a separate computer from the web server.

# Publishing to Sonatype OSSRH

To publish this package to the [Sonatype OSSRH], we follow the workflow
detailed on [the Sonatype website]. Note that you will need to set up a
[Maven settings.xml file] with your GPG settings in order to sign the
package for publication.

Once you're set up, you can run `mvn clean deploy` to publish the package
to the OSSRH. If your version number ends in `-SNAPSHOT`, this will be
published to the OSSRH Snapshots repository.

  [clades]: https://en.wikipedia.org/wiki/Clade
  [de Queiroz, 2007]: https://doi.org/10.1080/10635150701656378
  [Phyloreferencing]: https://www.phyloref.org/
  [Cellinese et al., preprint]: https://doi.org/10.32942/osf.io/57yjs
  [ontologies]: https://en.wikipedia.org/wiki/Ontology
  [Web Ontology Language]: https://www.w3.org/OWL/
  [Test Anything Protocol]: http://testanything.org/
  [JSON]: https://www.json.org/
  [OWL API]: https://github.com/owlcs/owlapi
  [the JOSS manuscript]: ./paper/paper.md
  [phyx.js]: https://github.com/phyloref/phyx.js
  [Elk reasoner]: http://liveontologies.github.io/elk-reasoner/
  [Java]: https://www.java.com/en/
  [Apache Maven]: https://maven.apache.org/
  [Homebrew]: https://brew.sh/
  [jEnv]: https://www.jenv.be/
  [Sonatype OSSRH]: https://central.sonatype.org/pages/ossrh-guide.html
  [the Sonatype website]: https://central.sonatype.org/pages/apache-maven.html
  [Maven settings.xml file]: https://central.sonatype.org/pages/apache-maven.html
  [Coursier]: https://get-coursier.io/
  [Javadoc]: https://en.wikipedia.org/wiki/Javadoc
  [at javadoc.io]: https://javadoc.io/doc/org.phyloref/jphyloref
  [JPhyloRef Usage document]: ./Usage.md
  [our Github repository]: https://github.com/phyloref/jphyloref
  [an issue tracker]: https://github.com/phyloref/jphyloref/issues
  [continuous testing workflow]: https://github.com/phyloref/jphyloref/actions?query=workflow%3A%22Build+with+Maven%22
