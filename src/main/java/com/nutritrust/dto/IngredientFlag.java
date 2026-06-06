package com.nutritrust.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngredientFlag(
        String category,
        List<String> matchedTerms,
        String explanation,
        String taxonomyId,
        String displayName,
        List<String> classes,
        List<String> matchedTaxonomyIds,
        String description,
        String functionDescription,
        List<String> sourceUrls
) {
    public IngredientFlag(String category, List<String> matchedTerms, String explanation) {
        this(category, matchedTerms, explanation, null, null, List.of(), List.of(), null, null, List.of());
    }
}
