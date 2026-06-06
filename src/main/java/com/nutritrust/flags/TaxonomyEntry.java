package com.nutritrust.flags;

import java.util.List;

public record TaxonomyEntry(
        String id,
        String taxonomy,
        List<String> names,
        List<String> aliases,
        List<String> codes,
        List<String> parents,
        List<String> classes,
        List<String> allergens,
        String nova,
        String fromPalmOil,
        String vegan,
        String vegetarian,
        String description,
        String wikipediaUrl,
        String wikidataId
) {
}
