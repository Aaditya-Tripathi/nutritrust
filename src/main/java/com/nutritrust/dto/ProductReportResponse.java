package com.nutritrust.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductReportResponse(
        boolean found,
        String barcode,
        String productName,
        String brand,
        String category,
        String ingredientText,
        List<NutritionFlag> nutritionFlags,
        List<IngredientFlag> ingredientFlags,
        List<AdditiveFlag> additiveFlags,
        List<AllergenFlag> allergenFlags,
        List<PositiveSignal> positiveSignals,
        List<DataQualityWarning> dataQualityWarnings,
        String aiReport
) {
    public static ProductReportResponse notFound(String barcode) {
        return new ProductReportResponse(
                false,
                barcode,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "AI explanation unavailable. Please review the factual flags."
        );
    }
}
