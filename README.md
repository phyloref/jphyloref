# JPhyloRef

[![Build Status](https://github.com/phyloref/jphyloref/workflows/Build%20with%20Maven/badge.svg)](https://github.com/phyloref/jphyloref/actions?query=workflow%3A%22Build+with+Maven%22)
[![Maven Central](https://img.shields.io/maven-central/v/org.phyloref/jphyloref.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.phyloref%22%20AND%20a:%22jphyloref%22)
[![javadoc](https://javadoc.io/badge2/org.phyloref/jphyloref/javadoc.svg)](https://javadoc.io/doc/org.phyloref/jphyloref)
[![DOI](https://zenodo.org/badge/104808310.svg)](https://zenodo.org/badge/latestdoi/104808310)

In evolutionary biology, groups of organisms consisting of an ancestor and all of its descendants ("clades") are a
fundamental unit for understanding evolution and describing biodiversity (see [de Queiroz, 2007]). Phylogenetic clade
definitions define clades based on shared ancestry, providing the theoretical foundation for the semantics of taxon
concepts to be defined and reproducibly resolved within a hypothesis of evolutionary relationships, i.e., a phylogeny.
We have proposed a mechanism, called [Phyloreferencing], for representing phylogenetic clade definitions as structured
data with fully machine-processable semantics, using the [Web Ontology Language] (OWL). We refer to such
machine-interpretable clade definitions as "phyloreferences" (see [Cellinese et al., preprint]). For more information
on how phyloreferences are implemented, see [the JOSS manuscript] included in this repository.

JPhyloRef is a [Java]-based command line tool as well as a web service for reasoning with [ontologies] in OWL that
contain phyloreferences and their accompanying reference phylogenetic
trees. It has two main goals:

1. The primary one is to facilitate automated testing that the semantics
of the logical definitions imply ("resolve to") the correct nodes in the reference tree as clade ancestors. This is key in
supporting quality control for the digitization of phylogenetic clade definitions from natural language text to a structured
machine-interpretable representation. It also verifies that one of the theoretical foundational premises of phyloreferences,
computational reproducibility, holds in practice.
2. The secondary goal is to enable integration with external tools that need
to obtain the clade ancestor node(s) resulting from a given ontology of phyloreferences and reference tree(s). When run as
part of an automated testing workflow, JPhyloRef reports test results in the cross-platform [Test Anything Protocol] (TAP)
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

Documentation of the API is included in the source code as [Javadoc] comments. It is also available online [at javadoc.io].
Detailed usage instructions are included in the [JPhyloRef Usage document].

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
    reasoner. Phyloreferences should currently be resolved using this reasoner.

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
$ java -jar target/jphyloref-1.0.0.jar test src/test/resources/phylorefs/dummy1.owl
```

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

  [de Queiroz, 2007]: https://doi.org/10.1080/10635150701656378
  [Phyloreferencing]: https://www.phyloref.org/
  [Cellinese et al., preprint]: https://doi.org/10.32942/osf.io/57yjs
  [ontologies]: https://en.wikipedia.org/wiki/Ontology
  [Web Ontology Language]: https://www.w3.org/OWL/
  [Test Anything Protocol]: http://testanything.org/
  [JSON]: https://www.json.org/
  [OWL API]: https://github.com/owlcs/owlapi
  [the JOSS manuscript]: ./paper/paper.md
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
