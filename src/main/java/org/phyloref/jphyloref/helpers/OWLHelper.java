package org.phyloref.jphyloref.helpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * OWLHelper contains helpful functions.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 *
 */
public final class OWLHelper {
    private static OWLAnnotationProperty cache_labelProperty = null;
    public static OWLAnnotationProperty getLabelProperty(OWLOntology ontology) {
        if(cache_labelProperty != null) return cache_labelProperty;
        cache_labelProperty = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
        return cache_labelProperty;
    }
    
    public static Set<String> getLabelsInEnglish(OWLNamedIndividual individual, OWLOntology ontology) {
        OWLAnnotationProperty labelProperty = getLabelProperty(ontology);
        return OWLHelper.getAnnotationLiteralsForEntity(ontology, individual, labelProperty, Arrays.asList("", "en"));
    }
    
    public static Map<String, Set<String>> getAnnotationLiteralsForEntity(OWLOntology ontology, OWLEntity entity, OWLAnnotationProperty annotationProperty) {
        Map<String, Set<String>> valuesByLanguage = new HashMap<>();

        for (OWLAnnotation annotation : entity.getAnnotations(ontology, annotationProperty)) {
            if (annotation.getValue() instanceof OWLLiteral) {
                OWLLiteral val = (OWLLiteral) annotation.getValue();
                String lang = val.getLang();

                if (!valuesByLanguage.containsKey(lang)) {
                    valuesByLanguage.put(lang, new HashSet<>());
                }

                valuesByLanguage.get(lang).add(val.getLiteral());
            }
        }

        return valuesByLanguage;
    }

    public static Set<String> getAnnotationLiteralsForEntity(OWLOntology ontology, OWLEntity entity, OWLAnnotationProperty annotationProperty, List<String> langs) {
        Map<String, Set<String>> valuesByLanguage = getAnnotationLiteralsForEntity(ontology, entity, annotationProperty);

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
}
