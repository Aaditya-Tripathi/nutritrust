package com.nutritrust.flags;

import java.util.List;

public record FlagRule(
        String id,
        FlagType type,
        String category,
        Boolean enabled,
        MatchMode matchMode,
        List<String> terms,
        List<String> taxonomyIds,
        List<String> taxonomyClassIds,
        String explanation,
        String description,
        String functionDescription,
        List<String> sourceUrls,
        String nutrientName,
        List<String> nutrientFields,
        List<String> fallbackNutrientFields,
        Double fallbackMultiplier,
        String unit,
        String fallbackExplanation,
        List<NutritionLevelDefinition> levels,
        List<String> positiveSignalLevels
) {
    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
