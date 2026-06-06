package com.nutritrust.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductLookupResponse(
        boolean found,
        String barcode,
        String productName,
        String brand,
        String category,
        String ingredients,
        String allergens,
        List<String> additives,
        String imageUrl,
        NutritionDto nutrition,
        String dataSource,
        String message
) {
    public static ProductLookupResponse notFound(String barcode) {
        return new ProductLookupResponse(
                false,
                barcode,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Product not found in Open Food Facts database"
        );
    }
}
