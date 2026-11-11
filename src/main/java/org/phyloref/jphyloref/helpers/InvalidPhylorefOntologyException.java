package org.phyloref.jphyloref.helpers;

/**
 * Thrown when an ontology cannot be searched for phyloreferences, such as one that does not define
 * phyloref:Phyloreference exactly once. This is a problem with the input, so the webserver answers
 * it with a 400 rather than a 500; it extends IllegalArgumentException so that callers catching
 * that (such as the `resolve` command) still handle it.
 */
public class InvalidPhylorefOntologyException extends IllegalArgumentException {
  public InvalidPhylorefOntologyException(String message) {
    super(message);
  }
}
