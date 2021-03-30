# JPhyloRef

[![Build Status](https://github.com/phyloref/jphyloref/workflows/Build%20with%20Maven/badge.svg)](https://github.com/phyloref/jphyloref/actions?query=workflow%3A%22Build+with+Maven%22)
[![javadoc](https://javadoc.io/badge2/org.phyloref/jphyloref/javadoc.svg)](https://javadoc.io/doc/org.phyloref/jphyloref) 

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
- `java -jar jphyloref.jar test input.owl`: Test all the phyloreferences in
  `input.owl` by comparing their resolution with the expected resolution recorded
  in the file.

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

  [Sonatype OSSRH]: https://central.sonatype.org/pages/ossrh-guide.html
  [the Sonatype website]: https://central.sonatype.org/pages/apache-maven.html
  [Maven settings.xml file]: https://central.sonatype.org/pages/apache-maven.html
