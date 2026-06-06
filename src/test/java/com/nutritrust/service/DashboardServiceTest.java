package com.nutritrust.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritrust.dto.AdditiveFlag;
import com.nutritrust.dto.AllergenFlag;
import com.nutritrust.dto.DashboardResponse;
import com.nutritrust.dto.DataQualityWarning;
import com.nutritrust.dto.IngredientFlag;
import com.nutritrust.dto.NutritionFlag;
import com.nutritrust.dto.PositiveSignal;
import com.nutritrust.entity.ProductReportEntity;
import com.nutritrust.repository.ProductReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductReportRepository repository = mock(ProductReportRepository.class);
    private final DashboardService service = new DashboardService(repository, objectMapper);

    @Test
    void returnsEmptyDashboardWhenNoReportsExist() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        DashboardResponse response = service.getDashboard();

        assertThat(response.summary().totalReports()).isZero();
        assertThat(response.summary().highSugarReports()).isZero();
        assertThat(response.summary().highSaltReports()).isZero();
        assertThat(response.summary().saturatedFatReports()).isZero();
        assertThat(response.summary().allergenWarningReports()).isZero();
        assertThat(response.summary().missingDataReports()).isZero();
        assertThat(response.summary().ingredientAdditiveReports()).isZero();
        assertThat(response.flagDistribution())
                .extracting(DashboardResponse.DashboardFlagDistributionItem::count)
                .containsOnly(0L);
        assertThat(response.recentReports()).isEmpty();
    }

    @Test
    void aggregatesNutritionWarningsAndDistributionCounts() throws Exception {
        ProductReportEntity highRisk = report(
                1L,
                "1111111111111",
                "High Risk Snack",
                List.of(
                        new NutritionFlag("Sugar", "HIGH", "28.00g per 100g", "Sugar is high."),
                        new NutritionFlag("Salt", "HIGH", "2.10g per 100g", "Salt is high."),
                        new NutritionFlag("Saturated fat", "MEDIUM", "3.00g per 100g", "Saturated fat is medium.")
                ),
                List.of(new IngredientFlag("Processed Oils / Fats", List.of("palm oil"), "Ingredient flag.")),
                List.of(new AdditiveFlag("e330", "additives_tags", "Additive flag.")),
                List.of(new AllergenFlag("milk", "allergens_tags", "Allergen flag.")),
                List.of(new PositiveSignal("Protein", "GOOD", "9.00g per 100g", "Protein is good.")),
                List.of(new DataQualityWarning("ingredients", "Ingredient data may be incomplete."))
        );
        ProductReportEntity sodiumRisk = report(
                2L,
                "2222222222222",
                "Sodium Risk Meal",
                List.of(new NutritionFlag("Sodium", "HIGH", "0.80g per 100g", "Sodium is high.")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new DataQualityWarning("allergens", "Allergen data may be incomplete."))
        );
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(highRisk, sodiumRisk));

        DashboardResponse response = service.getDashboard();

        assertThat(response.summary().totalReports()).isEqualTo(2);
        assertThat(response.summary().highSugarReports()).isEqualTo(1);
        assertThat(response.summary().highSaltReports()).isEqualTo(2);
        assertThat(response.summary().saturatedFatReports()).isEqualTo(1);
        assertThat(response.summary().allergenWarningReports()).isEqualTo(2);
        assertThat(response.summary().missingDataReports()).isEqualTo(2);
        assertThat(response.summary().ingredientAdditiveReports()).isEqualTo(1);
        assertThat(response.flagDistribution())
                .anySatisfy(item -> {
                    assertThat(item.category()).isEqualTo("Nutrition");
                    assertThat(item.count()).isEqualTo(4);
                })
                .anySatisfy(item -> {
                    assertThat(item.category()).isEqualTo("Ingredients");
                    assertThat(item.count()).isEqualTo(1);
                })
                .anySatisfy(item -> {
                    assertThat(item.category()).isEqualTo("Additives");
                    assertThat(item.count()).isEqualTo(1);
                })
                .anySatisfy(item -> {
                    assertThat(item.category()).isEqualTo("Allergens");
                    assertThat(item.count()).isEqualTo(1);
                })
                .anySatisfy(item -> {
                    assertThat(item.category()).isEqualTo("Data Quality");
                    assertThat(item.count()).isEqualTo(2);
                })
                .anySatisfy(item -> {
                    assertThat(item.category()).isEqualTo("Positive Signals");
                    assertThat(item.count()).isEqualTo(1);
                });
        assertThat(response.recentReports())
                .first()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(1);
                    assertThat(row.nutritionFlagCount()).isEqualTo(3);
                    assertThat(row.tags()).contains("High Sugar", "High Salt", "Ingredient flag", "Additive flag", "Allergen flag", "Missing data", "Positive signal");
                });
    }

    @Test
    void treatsMalformedAndBlankSavedJsonAsEmptyLists() {
        ProductReportEntity malformed = new ProductReportEntity();
        ReflectionTestUtils.setField(malformed, "id", 9L);
        malformed.setBarcode("9999999999999");
        malformed.setProductName("Malformed Report");
        malformed.setNutritionFlagsJson("[");
        malformed.setIngredientFlagsJson("");
        malformed.setAdditiveFlagsJson(null);
        malformed.setAllergenFlagsJson("not-json");
        malformed.setPositiveSignalsJson("null");
        malformed.setDataQualityWarningsJson("[]");
        ProductReportEntity nullElement = new ProductReportEntity();
        ReflectionTestUtils.setField(nullElement, "id", 10L);
        nullElement.setBarcode("1010101010101");
        nullElement.setProductName("Null Element Report");
        nullElement.setNutritionFlagsJson("[null]");
        nullElement.setIngredientFlagsJson("[null]");
        nullElement.setAdditiveFlagsJson("[null]");
        nullElement.setAllergenFlagsJson("[null]");
        nullElement.setPositiveSignalsJson("[null]");
        nullElement.setDataQualityWarningsJson("[null]");
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(malformed, nullElement));

        DashboardResponse response = service.getDashboard();

        assertThat(response.summary().totalReports()).isEqualTo(2);
        assertThat(response.flagDistribution())
                .extracting(DashboardResponse.DashboardFlagDistributionItem::count)
                .containsOnly(0L);
        assertThat(response.recentReports())
                .extracting(DashboardResponse.DashboardRecentReport::tags)
                .allSatisfy(tags -> assertThat(tags).isEmpty());
    }

    private ProductReportEntity report(
            Long id,
            String barcode,
            String productName,
            List<NutritionFlag> nutritionFlags,
            List<IngredientFlag> ingredientFlags,
            List<AdditiveFlag> additiveFlags,
            List<AllergenFlag> allergenFlags,
            List<PositiveSignal> positiveSignals,
            List<DataQualityWarning> warnings
    ) throws Exception {
        ProductReportEntity entity = new ProductReportEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", LocalDateTime.of(2026, 6, 7, 10, id.intValue()));
        entity.setBarcode(barcode);
        entity.setProductName(productName);
        entity.setBrand("Test Brand");
        entity.setCategory("Test Category");
        entity.setNutritionFlagsJson(objectMapper.writeValueAsString(nutritionFlags));
        entity.setIngredientFlagsJson(objectMapper.writeValueAsString(ingredientFlags));
        entity.setAdditiveFlagsJson(objectMapper.writeValueAsString(additiveFlags));
        entity.setAllergenFlagsJson(objectMapper.writeValueAsString(allergenFlags));
        entity.setPositiveSignalsJson(objectMapper.writeValueAsString(positiveSignals));
        entity.setDataQualityWarningsJson(objectMapper.writeValueAsString(warnings));
        return entity;
    }
}
