package com.nutritrust.dto;

public record NutritionDto(
        Double energyKcalPer100g,
        Double sugarPer100g,
        Double fatPer100g,
        Double saturatedFatPer100g,
        Double proteinPer100g,
        Double saltPer100g,
        Double sodiumPer100g,
        Double fiberPer100g
) {
}
