# Using FaCT++ with JPhyloRef

JPhyloRef picks up the latest version of FaCT++ published to a Maven repository
(currently [Factplusplus 1.5.2] on Berkeley BOP), but this doesn't include the
native libraries necessary to make the reasoner work. You will need to download
them separately from the [factplusplus Google Code Archive].

When invoking JPhyloRef from the command line, you may need to provide the path
to the native library using the `java.library.path` setting, such as:

    java -Djava.library.path='lib/factplusplus/FaCT++-OSX-v1.5.2/64bit/' -jar target/jphyloref-0.2-SNAPSHOT.jar webserver --reasoner fact++

[Factplusplus 1.5.2]: https://mvnrepository.com/artifact/uk.ac.manchester.cs/factplusplus/1.5.2
[factplusplus Google Code Archive]: https://code.google.com/archive/p/factplusplus/downloads
