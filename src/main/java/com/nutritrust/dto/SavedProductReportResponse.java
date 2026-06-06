package com.nutritrust.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SavedProductReportResponse(
        Long id,
        String barcode,
        String productName,
        String brand,
        String category,
        String ingredients,
        List<NutritionFlag> nutritionFlags,
        List<IngredientFlag> ingredientFlags,
        List<AdditiveFlag> additiveFlags,
        List<AllergenFlag> allergenFlags,
        List<PositiveSignal> positiveSignals,
        List<DataQualityWarning> dataQualityWarnings,
        String aiReport,
        LocalDateTime createdAt
) {
}
