package org.phyloref.jphyloref.helpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * OWLHelper contains methods simplify accessing information from the OWL API.
 *
 * @author Gaurav Vaidya
 */
public final class OWLHelper {
  /** A variable we use to cache rdfs:label */
  private static OWLAnnotationProperty cache_labelProperty = null;

  /**
   * Returns OWL property rdfs:label, using a cache so we don't need to load the property using the
   * data property.
   *
   * @param ontology The ontology whose OWLAnnotationProperty we want.
   * @return The OWLAnnotationProperty of rdfs:label.
   */
  public static OWLAnnotationProperty getLabelProperty(OWLOntology ontology) {
    if (cache_labelProperty != null) return cache_labelProperty;
    cache_labelProperty =
        ontology
            .getOWLOntologyManager()
            .getOWLDataFactory()
            .getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
    return cache_labelProperty;
  }

  /**
   * Return a list of labels for an OWLNamedIndividual in English or (if no such label is known)
   * with no language provided.
   *
   * @param individual The OWLNamedIndividual whose labels need to be retrieved.
   * @param ontology The ontology within which this individual is defined.
   * @return A list of labels as Java Strings.
   */
  public static Set<String> getLabelsInEnglish(
      OWLNamedIndividual individual, OWLOntology ontology) {
    OWLAnnotationProperty labelProperty = getLabelProperty(ontology);
    return OWLHelper.getAnnotationLiteralsForEntity(
        ontology, individual, labelProperty, Arrays.asList("en"));
  }

  /**
   * Extract literal values for an annotation property applied to an OWL entity in an ontology for a
   * particular set of languages. If no literals are found for any of the specified languages, we
   * will return literals that don't have an explicit language tag (i.e. an xsd:string instead of an
   * rdfs:langString). If no such literals are known, we return an empty set.
   *
   * @param ontology The ontology containing the entity and the annotation property to extract
   * @param entity The entity to extract annotations for
   * @param annotationProperty The annotation property to extract (usually rdfs:label)
   * @param langs Languages to extract annotation literals for, in order of importance
   * @return Set of annotation property values for the first matched language, or those associated
   *     with no languages
   */
  public static Set<String> getAnnotationLiteralsForEntity(
      OWLOntology ontology,
      OWLEntity entity,
      OWLAnnotationProperty annotationProperty,
      List<String> langs) {
    // Get the map of all strings for all languages for this annotation property
    Map<String, Set<String>> valuesByLanguage =
        getAnnotationLiteralsForEntity(ontology, entity, annotationProperty);

    // Look for the languages requested. Note that we return the annotation property
    // values for the first language we can find, NOT the union of all the annotation
    // properties.
    for (String lang : langs) {
      if (valuesByLanguage.containsKey(lang)) {
        return valuesByLanguage.get(lang);
      }
    }

    // Didn't find the lang? Use "".
    if (valuesByLanguage.containsKey("")) {
      return valuesByLanguage.get("");
    } else {
      return new HashSet<>();
    }
  }

  /**
   * Return a Map that contains all known values for a given annotation property for a given OWL
   * entity, grouped by language tags. The special language tag "" is used as the key for literals
   * without an explicit language tag.
   *
   * @param ontology The ontology containing the OWL entity and the annotation property to query
   * @param entity The OWL entity to query
   * @param annotationProperty The annotation property to query
   * @return A Map of annotation values organized into Sets by language.
   */
  public static Map<String, Set<String>> getAnnotationLiteralsForEntity(
      OWLOntology ontology, OWLEntity entity, OWLAnnotationProperty annotationProperty) {
    Map<String, Set<String>> valuesByLanguage = new HashMap<>();

    // Go through all known annotations, looking for OWLLiterals.
    EntitySearcher.getAnnotations(entity, ontology, annotationProperty)
        .stream()
        .filter(annotation -> annotation.getValue() instanceof OWLLiteral)
        .forEach(
            annotation -> {
              OWLLiteral val = (OWLLiteral) annotation.getValue();
              String lang = val.getLang();

              // Organize values by language.
              if (!valuesByLanguage.containsKey(lang)) {
                valuesByLanguage.put(lang, new HashSet<>());
              }

              valuesByLanguage.get(lang).add(val.getLiteral());
            });

    return valuesByLanguage;
  }
}
