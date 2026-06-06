package com.nutritrust.flags;

public record NutritionLevelDefinition(
        String level,
        Double minInclusive,
        Double maxInclusive
) {
}
