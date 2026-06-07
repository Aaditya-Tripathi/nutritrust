package com.nutritrust.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritrust.dto.ProductReportResponse;
import com.nutritrust.flags.FlagRuleEngine;
import com.nutritrust.flags.FlagRuleLoader;
import com.nutritrust.flags.OpenFoodFactsTaxonomyLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductReportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void honeyIngredientTextDoesNotCreateConfiguredSugarOrNutIngredientFlags() throws Exception {
        ProductLookupService lookupService = mock(ProductLookupService.class);
        AiReportService aiReportService = mock(AiReportService.class);
        ProductReportPersistenceService productReportPersistenceService = mock(ProductReportPersistenceService.class);
        ProductReportService reportService = new ProductReportService(
                lookupService,
                aiReportService,
                productReportPersistenceService,
                flagRuleEngine()
        );

        JsonNode openFoodFactsResponse = objectMapper.readTree("""
                {
                  "status": 1,
                  "code": "1234567890123",
                  "product": {
                    "product_name": "Honey",
                    "brands": "Generic",
                    "categories": "honey",
                    "ingredients_text": "Honey (100%)",
                    "additives_tags": [],
                    "nutriments": {
                      "sugars_100g": 80.0,
                      "saturated-fat_100g": 0.0,
                      "proteins_100g": 0.0,
                      "salt_100g": 0.0425
                    }
                  }
                }
                """);

        when(lookupService.lookupRawByBarcode("1234567890123")).thenReturn(openFoodFactsResponse);
        when(aiReportService.explain(
                eq("Honey"),
                eq("Generic"),
                eq("honey"),
                eq("Honey (100%)"),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                isNull()
        )).thenReturn("AI explanation unavailable. Please review the factual flags.");

        ProductReportResponse report = reportService.generateReport("1234567890123");

        assertThat(report.ingredientText()).isEqualTo("Honey (100%)");
        assertThat(report.ingredientFlags())
                .noneSatisfy(flag -> {
                    assertThat(flag.category()).isIn("Added Sugar / Sweetener Sources", "tree nut");
                });
        assertThat(report.allergenFlags()).isEmpty();
        assertThat(report.nutritionFlags())
                .anySatisfy(flag -> {
                    assertThat(flag.name()).isEqualTo("Sugar");
                    assertThat(flag.level()).isEqualTo("HIGH");
                    assertThat(flag.value()).isEqualTo("80.00g per 100g");
                });
    }

    @Test
    void reportResponseIncludesPositiveSignalsFromFetchedNutrition() throws Exception {
        ProductLookupService lookupService = mock(ProductLookupService.class);
        AiReportService aiReportService = mock(AiReportService.class);
        ProductReportPersistenceService productReportPersistenceService = mock(ProductReportPersistenceService.class);
        ProductReportService reportService = new ProductReportService(
                lookupService,
                aiReportService,
                productReportPersistenceService,
                flagRuleEngine()
        );

        JsonNode openFoodFactsResponse = objectMapper.readTree("""
                {
                  "status": 1,
                  "code": "8901088068758",
                  "product": {
                    "product_name": "Saffola Masala Veggie Twist Oats",
                    "brands": "Saffola",
                    "categories": "Breakfast foods",
                    "ingredients_text": "rolled oats, vegetables, spices",
                    "allergens": "",
                    "additives_tags": [],
                    "nutriments": {
                      "sugars_100g": 5.1,
                      "salt_100g": 5.7075,
                      "saturated-fat_100g": 3.4,
                      "proteins_100g": 8.8,
                      "fiber_100g": 12.4
                    }
                  }
                }
                """);

        when(lookupService.lookupRawByBarcode("8901088068758")).thenReturn(openFoodFactsResponse);
        when(aiReportService.explain(
                eq("Saffola Masala Veggie Twist Oats"),
                eq("Saffola"),
                eq("Breakfast foods"),
                eq("rolled oats, vegetables, spices"),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                isNull()
        )).thenReturn("AI explanation unavailable. Please review the factual flags.");

        ProductReportResponse report = reportService.generateReport("8901088068758");

        assertThat(report.positiveSignals())
                .extracting(signal -> signal.name())
                .contains("Protein", "Fiber");
    }

    private FlagRuleEngine flagRuleEngine() {
        return new FlagRuleEngine(
                new FlagRuleLoader(objectMapper),
                new OpenFoodFactsTaxonomyLoader(objectMapper)
        );
    }
}
