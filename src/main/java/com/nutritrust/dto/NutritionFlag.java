package com.nutritrust.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record NutritionFlag(
        String name,
        String level,
        String value,
        String explanation,
        String description,
        String functionDescription,
        List<String> sourceUrls
) {
    public NutritionFlag(String name, String level, String value, String explanation) {
        this(name, level, value, explanation, null, null, List.of());
    }
}
