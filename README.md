# JPhyloRef

[![Build Status](https://github.com/phyloref/jphyloref/workflows/Build%20with%20Maven/badge.svg)](https://github.com/phyloref/jphyloref/actions?query=workflow%3A%22Build+with+Maven%22)
[![Maven Central](https://img.shields.io/maven-central/v/org.phyloref/jphyloref.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.phyloref%22%20AND%20a:%22jphyloref%22)
[![javadoc](https://javadoc.io/badge2/org.phyloref/jphyloref/javadoc.svg)](https://javadoc.io/doc/org.phyloref/jphyloref)
[![DOI](https://zenodo.org/badge/104808310.svg)](https://zenodo.org/badge/latestdoi/104808310)

JPhyloRef wraps multiple OWL 2 reasoners and provides three ways in which they
can be used to resolve [phyloreferences](http://phyloref.org):

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

# Command line options

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
  - [FaCT++ 1.5.2](https://code.google.com/archive/p/factplusplus/) (`fact++`) is
    an OWL 2 DL reasoner. It requires version 1.5.2 of the Java Native Library for
    your operating system, which needs to be
    [downloaded from Google Code](https://code.google.com/archive/p/factplusplus/downloads?page=2).
  - [JFact 4.0.4](http://jfact.sourceforge.net/) (`jfact`) is an OWL 2 DL reasoner.
    Since it is written in pure Java, it is the slowest reasoner currently supported.

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
  [our Github repository]: https://github.com/phyloref/jphyloref
  [an issue tracker]: https://github.com/phyloref/jphyloref/issues
  [continuous testing workflow]: https://github.com/phyloref/jphyloref/actions?query=workflow%3A%22Build+with+Maven%22
