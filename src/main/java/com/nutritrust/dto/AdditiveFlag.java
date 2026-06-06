package com.nutritrust.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AdditiveFlag(
        String name,
        String source,
        String explanation,
        String taxonomyId,
        String displayName,
        List<String> classes,
        List<String> matchedTaxonomyIds,
        List<String> matchedTerms,
        String description,
        String functionDescription,
        List<String> sourceUrls
) {
    public AdditiveFlag(String name, String source, String explanation) {
        this(name, source, explanation, null, null, List.of(), List.of(), List.of(), null, null, List.of());
    }
}
