package com.nutritrust.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nutritrust.dto.AdditiveFlag;
import com.nutritrust.dto.DataQualityWarning;
import com.nutritrust.dto.ProductReportRequest;
import com.nutritrust.dto.ProductReportResponse;
import com.nutritrust.flags.FlagEvaluation;
import com.nutritrust.flags.FlagRuleEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductReportService {

    private static final String AI_UNAVAILABLE_MESSAGE = "AI explanation unavailable. Please review the factual flags.";

    private final ProductLookupService productLookupService;
    private final AiReportService aiReportService;
    private final ProductReportPersistenceService productReportPersistenceService;
    private final FlagRuleEngine flagRuleEngine;

    public ProductReportService(
            ProductLookupService productLookupService,
            AiReportService aiReportService,
            ProductReportPersistenceService productReportPersistenceService,
            FlagRuleEngine flagRuleEngine
    ) {
        this.productLookupService = productLookupService;
        this.aiReportService = aiReportService;
        this.productReportPersistenceService = productReportPersistenceService;
        this.flagRuleEngine = flagRuleEngine;
    }

    public ProductReportResponse generateReport(String barcode) {
        return generateReport(new ProductReportRequest(barcode, null, null, null));
    }

    public ProductReportResponse generateReport(ProductReportRequest request) {
        ProductReportRequest safeRequest = request == null ? new ProductReportRequest(null, null, null, null) : request;
        JsonNode root = productLookupService.lookupRawByBarcode(safeRequest.barcode());
        String barcode = safeRequest.barcode();
        if (!isProductFound(root)) {
            return ProductReportResponse.notFound(barcode);
        }

        JsonNode product = root.path("product");
        JsonNode nutriments = product.path("nutriments");
        String productName = textOrNull(product.path("product_name"));
        String brand = firstCsvValue(product.path("brands"));
        String category = firstCsvValue(product.path("categories"));
        String combinedIngredientText = mergeText(buildCombinedIngredientText(product), safeRequest.manualIngredientsText());
        String combinedAllergenText = mergeText(buildCombinedAllergenText(product), safeRequest.manualAllergenText());

        FlagEvaluation flagEvaluation = flagRuleEngine.evaluate(product, combinedIngredientText, combinedAllergenText);
        List<DataQualityWarning> dataQualityWarnings = buildDataQualityWarnings(
                product,
                nutriments,
                combinedIngredientText,
                combinedAllergenText,
                flagEvaluation.additiveFlags(),
                safeRequest.manualNutritionNote()
        );

        String aiReport = aiReportService.explain(
                productName,
                brand,
                category,
                combinedIngredientText,
                flagEvaluation.nutritionFlags(),
                flagEvaluation.ingredientFlags(),
                flagEvaluation.additiveFlags(),
                flagEvaluation.allergenFlags(),
                flagEvaluation.positiveSignals(),
                dataQualityWarnings
        );

        ProductReportResponse response = new ProductReportResponse(
                true,
                textOrDefault(root.path("code"), barcode),
                productName,
                brand,
                category,
                combinedIngredientText,
                flagEvaluation.nutritionFlags(),
                flagEvaluation.ingredientFlags(),
                flagEvaluation.additiveFlags(),
                flagEvaluation.allergenFlags(),
                flagEvaluation.positiveSignals(),
                dataQualityWarnings,
                aiReport == null || aiReport.isBlank() ? AI_UNAVAILABLE_MESSAGE : aiReport
        );
        productReportPersistenceService.saveReport(response);
        return response;
    }

    private boolean isProductFound(JsonNode root) {
        return root != null
                && !root.isMissingNode()
                && !root.isNull()
                && root.path("status").asInt(0) == 1
                && !root.path("product").isMissingNode()
                && !root.path("product").isNull();
    }

    private List<DataQualityWarning> buildDataQualityWarnings(
            JsonNode product,
            JsonNode nutriments,
            String combinedIngredientText,
            String combinedAllergenText,
            List<AdditiveFlag> additiveFlags,
            String manualNutritionNote
    ) {
        Map<String, DataQualityWarning> warnings = new LinkedHashMap<>();
        String normalizedIngredients = normalizeText(combinedIngredientText);
        if (normalizedIngredients.isBlank() || normalizedIngredients.length() < 20) {
            putWarning(warnings, "ingredients", "Ingredient data from the source may be incomplete. Ingredient flags depend on available product data.");
        }

        List<String> missingNutritionFields = missingNutritionFields(nutriments);
        if (!missingNutritionFields.isEmpty()) {
            putWarning(warnings, "nutrition", "Some nutrition fields are missing from the source data: " + String.join(", ", missingNutritionFields) + ".");
        }

        if (normalizeText(combinedAllergenText).isBlank()) {
            putWarning(warnings, "allergens", "Allergen and trace data from the source may be incomplete or unavailable.");
        }

        boolean additiveArraysEmpty = textValues(product.path("additives_tags")).isEmpty()
                && textValues(product.path("additives_original_tags")).isEmpty()
                && textValues(product.path("additives_old_tags")).isEmpty();
        boolean additiveCodesFoundInText = additiveFlags.stream().anyMatch(flag -> "ingredients_text".equals(flag.source()));
        if (additiveArraysEmpty && additiveCodesFoundInText) {
            putWarning(warnings, "additives", "Additive arrays are empty, but additive codes were detected in product text.");
        }

        if (textOrNull(product.path("product_name")) == null || firstCsvValue(product.path("brands")) == null || firstCsvValue(product.path("categories")) == null) {
            putWarning(warnings, "productDetails", "Product details are fetched, but some label fields are unavailable.");
        }

        if (manualNutritionNote != null && !manualNutritionNote.isBlank()) {
            putWarning(warnings, "manualNutritionNote", "A manual nutrition note was provided. Nutrition flags still depend on structured nutrition values available from the source.");
        }

        if (!warnings.isEmpty()) {
            putWarning(warnings, "sourceData", "Open Food Facts data may be incomplete. Reviewers should interpret flags based on the available product data.");
        }
        return new ArrayList<>(warnings.values());
    }

    private List<String> missingNutritionFields(JsonNode nutriments) {
        if (nutriments == null || nutriments.isMissingNode() || nutriments.isNull()) {
            return List.of("sugars_100g", "salt_100g or sodium_100g", "saturated-fat_100g", "proteins_100g", "fiber_100g");
        }

        List<String> missing = new ArrayList<>();
        if (!hasAnyNutritionValue(nutriments, List.of("sugars_100g", "sugars_prepared_100g"))) {
            missing.add("sugars_100g");
        }
        if (!hasAnyNutritionValue(nutriments, List.of("salt_100g", "salt_prepared_100g", "sodium_100g", "sodium_prepared_100g"))) {
            missing.add("salt_100g or sodium_100g");
        }
        if (!hasAnyNutritionValue(nutriments, List.of("saturated-fat_100g", "saturated-fat_prepared_100g"))) {
            missing.add("saturated-fat_100g");
        }
        if (!hasAnyNutritionValue(nutriments, List.of("proteins_100g", "proteins_prepared_100g"))) {
            missing.add("proteins_100g");
        }
        if (!hasAnyNutritionValue(nutriments, List.of("fiber_100g", "fiber_prepared_100g"))) {
            missing.add("fiber_100g");
        }
        return missing;
    }

    private boolean hasAnyNutritionValue(JsonNode nutriments, List<String> fields) {
        for (String field : fields) {
            if (doubleOrNull(nutriments.path(field)) != null) {
                return true;
            }
        }
        return false;
    }

    private void putWarning(Map<String, DataQualityWarning> warnings, String field, String message) {
        warnings.putIfAbsent(field, new DataQualityWarning(field, message));
    }

    private String buildCombinedIngredientText(JsonNode product) {
        List<String> parts = new ArrayList<>();
        addFieldText(parts, product, "ingredients_text");
        addFieldText(parts, product, "ingredients_text_en");
        addFieldText(parts, product, "ingredients_text_with_allergens");
        addFieldText(parts, product, "ingredients");
        addFieldText(parts, product, "ingredients_original_tags");
        return String.join(" ", deduplicate(parts));
    }

    private String buildCombinedAllergenText(JsonNode product) {
        List<String> parts = new ArrayList<>();
        addFieldText(parts, product, "ingredients_text_with_allergens");
        addFieldText(parts, product, "allergens");
        addFieldText(parts, product, "allergens_tags");
        addFieldText(parts, product, "allergens_from_ingredients");
        addFieldText(parts, product, "traces");
        addFieldText(parts, product, "traces_tags");
        addFieldText(parts, product, "traces_from_ingredients");
        return String.join(" ", deduplicate(parts));
    }

    private void addFieldText(List<String> parts, JsonNode product, String field) {
        if (product == null || product.isMissingNode() || product.isNull()) {
            return;
        }
        for (String value : labelTextValues(product.path(field))) {
            String cleaned = cleanText(value);
            if (cleaned != null) {
                parts.add(cleaned);
            }
        }
    }

    private String mergeText(String sourceText, String manualText) {
        List<String> parts = new ArrayList<>();
        String source = cleanText(sourceText);
        String manual = cleanText(manualText);
        if (source != null) {
            parts.add(source);
        }
        if (manual != null) {
            parts.add(manual);
        }
        return String.join(" ", deduplicate(parts));
    }

    private List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        collectTextValues(node, values);
        return deduplicate(values);
    }

    private void collectTextValues(JsonNode node, List<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            values.add(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectTextValues(item, values));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectTextValues(entry.getValue(), values));
        }
    }

    private List<String> labelTextValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        collectLabelTextValues(node, values);
        return deduplicate(values);
    }

    private void collectLabelTextValues(JsonNode node, List<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            values.add(node.asText());
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectLabelTextValues(item, values));
            return;
        }
        if (node.isObject()) {
            addObjectTextField(values, node, "text");
            addObjectTextField(values, node, "id");
        }
    }

    private void addObjectTextField(List<String> values, JsonNode node, String field) {
        String value = textOrNull(node.path(field));
        if (value != null) {
            values.add(cleanTag(value));
        }
    }

    private List<String> deduplicate(List<String> list) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> values = new ArrayList<>();
        if (list == null) {
            return values;
        }

        for (String value : list) {
            String cleaned = cleanText(value);
            if (cleaned == null) {
                continue;
            }
            String key = normalizeText(cleaned);
            if (seen.add(key)) {
                values.add(cleaned);
            }
        }
        return values;
    }

    private String cleanTag(String value) {
        String cleaned = cleanText(value);
        if (cleaned == null) {
            return null;
        }
        int prefixSeparator = cleaned.indexOf(':');
        if (prefixSeparator >= 0 && prefixSeparator < cleaned.length() - 1) {
            return cleaned.substring(prefixSeparator + 1).trim();
        }
        return cleaned;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("[^a-z0-9()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return cleanText(value);
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        String value = textOrNull(node);
        return value == null ? defaultValue : value;
    }

    private String firstCsvValue(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }

        String firstValue = value.split(",", 2)[0].trim();
        return firstValue.isBlank() ? null : firstValue;
    }

    private Double doubleOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
