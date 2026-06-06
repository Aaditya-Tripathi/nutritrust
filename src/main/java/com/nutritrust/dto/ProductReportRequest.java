package com.nutritrust.dto;

public record ProductReportRequest(
        String barcode,
        String manualIngredientsText,
        String manualAllergenText,
        String manualNutritionNote
) {
}
