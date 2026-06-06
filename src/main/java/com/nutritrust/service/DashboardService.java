package com.nutritrust.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class DashboardService {

    private final ProductReportRepository productReportRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(ProductReportRepository productReportRepository, ObjectMapper objectMapper) {
        this.productReportRepository = productReportRepository;
        this.objectMapper = objectMapper;
    }

    public DashboardResponse getDashboard() {
        List<ProductReportEntity> reports = productReportRepository.findAllByOrderByCreatedAtDesc();
        long highSugarReports = 0;
        long highSaltReports = 0;
        long saturatedFatReports = 0;
        long allergenWarningReports = 0;
        long missingDataReports = 0;
        long ingredientAdditiveReports = 0;

        long nutritionCount = 0;
        long ingredientCount = 0;
        long additiveCount = 0;
        long allergenCount = 0;
        long dataQualityCount = 0;
        long positiveSignalCount = 0;

        List<DashboardResponse.DashboardRecentReport> recentReports = new ArrayList<>();
        for (ProductReportEntity report : reports) {
            ReportFacts facts = factsFor(report);
            nutritionCount += facts.nutritionFlags().size();
            ingredientCount += facts.ingredientFlags().size();
            additiveCount += facts.additiveFlags().size();
            allergenCount += facts.allergenFlags().size();
            dataQualityCount += facts.dataQualityWarnings().size();
            positiveSignalCount += facts.positiveSignals().size();

            if (hasHighNutritionFlag(facts.nutritionFlags(), "sugar")) {
                highSugarReports++;
            }
            if (hasHighNutritionFlag(facts.nutritionFlags(), "salt") || hasHighNutritionFlag(facts.nutritionFlags(), "sodium")) {
                highSaltReports++;
            }
            if (hasNutritionFlag(facts.nutritionFlags(), "saturated fat")) {
                saturatedFatReports++;
            }
            if (!facts.allergenFlags().isEmpty() || hasWarningFor(facts.dataQualityWarnings(), "allergen")) {
                allergenWarningReports++;
            }
            if (!facts.dataQualityWarnings().isEmpty()) {
                missingDataReports++;
            }
            if (!facts.ingredientFlags().isEmpty() || !facts.additiveFlags().isEmpty()) {
                ingredientAdditiveReports++;
            }

            recentReports.add(rowFor(report, facts));
        }

        DashboardResponse.DashboardSummary summary = new DashboardResponse.DashboardSummary(
                reports.size(),
                highSugarReports,
                highSaltReports,
                saturatedFatReports,
                allergenWarningReports,
                missingDataReports,
                ingredientAdditiveReports
        );
        return new DashboardResponse(
                summary,
                List.of(
                        new DashboardResponse.DashboardFlagDistributionItem("Nutrition", nutritionCount),
                        new DashboardResponse.DashboardFlagDistributionItem("Ingredients", ingredientCount),
                        new DashboardResponse.DashboardFlagDistributionItem("Additives", additiveCount),
                        new DashboardResponse.DashboardFlagDistributionItem("Allergens", allergenCount),
                        new DashboardResponse.DashboardFlagDistributionItem("Data Quality", dataQualityCount),
                        new DashboardResponse.DashboardFlagDistributionItem("Positive Signals", positiveSignalCount)
                ),
                recentReports
        );
    }

    private DashboardResponse.DashboardRecentReport rowFor(ProductReportEntity report, ReportFacts facts) {
        return new DashboardResponse.DashboardRecentReport(
                report.getId(),
                report.getBarcode(),
                report.getProductName(),
                report.getBrand(),
                report.getCategory(),
                report.getCreatedAt(),
                facts.nutritionFlags().size(),
                facts.ingredientFlags().size(),
                facts.additiveFlags().size(),
                facts.allergenFlags().size(),
                facts.dataQualityWarnings().size(),
                facts.positiveSignals().size(),
                tagsFor(facts)
        );
    }

    private ReportFacts factsFor(ProductReportEntity report) {
        return new ReportFacts(
                readList(report.getNutritionFlagsJson(), new TypeReference<List<NutritionFlag>>() {}),
                readList(report.getIngredientFlagsJson(), new TypeReference<List<IngredientFlag>>() {}),
                readList(report.getAdditiveFlagsJson(), new TypeReference<List<AdditiveFlag>>() {}),
                readList(report.getAllergenFlagsJson(), new TypeReference<List<AllergenFlag>>() {}),
                readList(report.getPositiveSignalsJson(), new TypeReference<List<PositiveSignal>>() {}),
                readList(report.getDataQualityWarningsJson(), new TypeReference<List<DataQualityWarning>>() {})
        );
    }

    private List<String> tagsFor(ReportFacts facts) {
        Set<String> tags = new LinkedHashSet<>();
        for (NutritionFlag flag : facts.nutritionFlags()) {
            tags.add("Nutrition");
            if ("HIGH".equalsIgnoreCase(flag.level())) {
                tags.add("High " + safeName(flag.name()));
            }
        }
        if (!facts.ingredientFlags().isEmpty()) {
            tags.add("Ingredient flag");
        }
        if (!facts.additiveFlags().isEmpty()) {
            tags.add("Additive flag");
        }
        if (!facts.allergenFlags().isEmpty()) {
            tags.add("Allergen flag");
        }
        if (hasWarningFor(facts.dataQualityWarnings(), "allergen")) {
            tags.add("Allergen warning");
        }
        if (!facts.dataQualityWarnings().isEmpty()) {
            tags.add("Missing data");
        }
        if (!facts.positiveSignals().isEmpty()) {
            tags.add("Positive signal");
        }
        return new ArrayList<>(tags);
    }

    private boolean hasHighNutritionFlag(List<NutritionFlag> flags, String name) {
        return flags.stream().anyMatch(flag -> matchesNutritionName(flag, name) && "HIGH".equalsIgnoreCase(flag.level()));
    }

    private boolean hasNutritionFlag(List<NutritionFlag> flags, String name) {
        return flags.stream().anyMatch(flag -> matchesNutritionName(flag, name));
    }

    private boolean matchesNutritionName(NutritionFlag flag, String name) {
        return normalize(flag.name()).equals(normalize(name));
    }

    private boolean hasWarningFor(List<DataQualityWarning> warnings, String field) {
        String normalizedField = normalize(field);
        return warnings.stream().anyMatch(warning -> normalize(warning.field()).contains(normalizedField));
    }

    private String safeName(String value) {
        return value == null || value.isBlank() ? "nutrition" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> values = objectMapper.readValue(json, typeReference);
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return List.of();
        }
    }

    private record ReportFacts(
            List<NutritionFlag> nutritionFlags,
            List<IngredientFlag> ingredientFlags,
            List<AdditiveFlag> additiveFlags,
            List<AllergenFlag> allergenFlags,
            List<PositiveSignal> positiveSignals,
            List<DataQualityWarning> dataQualityWarnings
    ) {
    }
}
