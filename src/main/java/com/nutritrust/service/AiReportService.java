package com.nutritrust.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritrust.dto.AdditiveFlag;
import com.nutritrust.dto.AllergenFlag;
import com.nutritrust.dto.DataQualityWarning;
import com.nutritrust.dto.IngredientFlag;
import com.nutritrust.dto.NutritionFlag;
import com.nutritrust.dto.PositiveSignal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiReportService {

    private static final Logger log = LoggerFactory.getLogger(AiReportService.class);

    private static final String AI_UNAVAILABLE_MESSAGE = "AI explanation unavailable. Please review the factual flags.";
    private static final String REQUIRED_FINAL_SENTENCE = "The final decision should be made by the reviewer based on their quality standards.";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AiReportService(
            ObjectMapper objectMapper,
            @Value("${groq.api.key:}") String apiKey,
            @Value("${groq.model:llama-3.1-8b-instant}") String model,
            @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    public String explain(
            String productName,
            String brand,
            String category,
            String ingredientText,
            List<NutritionFlag> nutritionFlags,
            List<IngredientFlag> ingredientFlags,
            List<AdditiveFlag> additiveFlags,
            List<AllergenFlag> allergenFlags,
            List<PositiveSignal> positiveSignals,
            List<DataQualityWarning> dataQualityWarnings
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Groq API key is not configured. Set GROQ_API_KEY before starting the app.");
            return buildFallbackReport(productName, brand, category, nutritionFlags, ingredientFlags, additiveFlags, allergenFlags, positiveSignals, dataQualityWarnings);
        }

        try {
            String prompt = buildPrompt(productName, brand, category, ingredientText, nutritionFlags, ingredientFlags, additiveFlags, allergenFlags, positiveSignals, dataQualityWarnings);
            Map<String, Object> request = Map.of(
                    "model", model,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", prompt
                    )),
                    "temperature", 0,
                    "max_tokens", 900
            );

            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            String explanation = normalizeExplanation(extractText(response));
            if (AI_UNAVAILABLE_MESSAGE.equals(explanation)
                    || contradictsGeneratedFlags(explanation, ingredientFlags, additiveFlags, allergenFlags)) {
                return buildFallbackReport(productName, brand, category, nutritionFlags, ingredientFlags, additiveFlags, allergenFlags, positiveSignals, dataQualityWarnings);
            }
            return explanation;
        } catch (RuntimeException ex) {
            log.warn("Groq report generation failed: {}", ex.getMessage());
            return buildFallbackReport(productName, brand, category, nutritionFlags, ingredientFlags, additiveFlags, allergenFlags, positiveSignals, dataQualityWarnings);
        }
    }

    private String buildFallbackReport(
            String productName,
            String brand,
            String category,
            List<NutritionFlag> nutritionFlags,
            List<IngredientFlag> ingredientFlags,
            List<AdditiveFlag> additiveFlags,
            List<AllergenFlag> allergenFlags,
            List<PositiveSignal> positiveSignals,
            List<DataQualityWarning> dataQualityWarnings
    ) {
        return """
                Product Overview:
                %s is listed%s%s. This local reviewer narrative was generated from backend rule results only.

                Nutrition Observations:
                %s

                Ingredient Observations:
                %s

                Additive and Allergen Observations:
                %s

                Data Completeness:
                %s

                Reviewer Note:
                %s
                """.formatted(
                safeText(productName),
                brand == null || brand.isBlank() ? "" : " by " + brand,
                category == null || category.isBlank() ? "" : " in " + category,
                nutritionSummary(nutritionFlags, positiveSignals),
                ingredientSummary(ingredientFlags),
                additiveAllergenSummary(additiveFlags, allergenFlags),
                dataQualitySummary(dataQualityWarnings),
                REQUIRED_FINAL_SENTENCE
        ).trim();
    }

    private String nutritionSummary(List<NutritionFlag> nutritionFlags, List<PositiveSignal> positiveSignals) {
        if ((nutritionFlags == null || nutritionFlags.isEmpty()) && (positiveSignals == null || positiveSignals.isEmpty())) {
            return "No nutrition flags or positive nutrition signals were generated from the available structured values.";
        }

        StringBuilder summary = new StringBuilder();
        if (nutritionFlags != null && !nutritionFlags.isEmpty()) {
            summary.append("Generated nutrition flags: ")
                    .append(nutritionFlags.stream()
                            .limit(8)
                            .map(flag -> flag.name() + " " + flag.level() + " (" + flag.value() + ")")
                            .toList());
            if (nutritionFlags.size() > 8) {
                summary.append(" and ").append(nutritionFlags.size() - 8).append(" more");
            }
            summary.append(".");
        }
        if (positiveSignals != null && !positiveSignals.isEmpty()) {
            if (!summary.isEmpty()) {
                summary.append(" ");
            }
            summary.append("Positive signals: ")
                    .append(positiveSignals.stream()
                            .limit(5)
                            .map(signal -> signal.name() + " " + signal.level())
                            .toList())
                    .append(".");
        }
        return summary.toString();
    }

    private String ingredientSummary(List<IngredientFlag> ingredientFlags) {
        if (ingredientFlags == null || ingredientFlags.isEmpty()) {
            return "No ingredient category flags were generated from the available ingredient data.";
        }
        String summary = "Generated ingredient category flags: " + ingredientFlags.stream()
                .limit(8)
                .map(flag -> flag.category() + matchedTermsSuffix(flag.matchedTerms()))
                .toList();
        if (ingredientFlags.size() > 8) {
            summary += " and " + (ingredientFlags.size() - 8) + " more";
        }
        return summary + ".";
    }

    private String additiveAllergenSummary(List<AdditiveFlag> additiveFlags, List<AllergenFlag> allergenFlags) {
        StringBuilder summary = new StringBuilder();
        if (additiveFlags == null || additiveFlags.isEmpty()) {
            summary.append("No additive flags were generated from the available product data.");
        } else {
            summary.append("Generated additive flags: ")
                    .append(additiveFlags.stream()
                            .limit(8)
                            .map(flag -> safeText(flag.displayName() == null ? flag.name() : flag.displayName()))
                            .toList());
            if (additiveFlags.size() > 8) {
                summary.append(" and ").append(additiveFlags.size() - 8).append(" more");
            }
            summary.append(".");
        }

        if (allergenFlags == null || allergenFlags.isEmpty()) {
            summary.append(" No allergen flags were generated from the available product data.");
        } else {
            summary.append(" Generated allergen flags: ")
                    .append(allergenFlags.stream()
                            .limit(8)
                            .map(flag -> safeText(flag.displayName() == null ? flag.name() : flag.displayName()))
                            .toList());
            if (allergenFlags.size() > 8) {
                summary.append(" and ").append(allergenFlags.size() - 8).append(" more");
            }
            summary.append(".");
        }
        return summary.toString();
    }

    private String dataQualitySummary(List<DataQualityWarning> dataQualityWarnings) {
        if (dataQualityWarnings == null || dataQualityWarnings.isEmpty()) {
            return "No major data completeness warnings were generated.";
        }
        String summary = "Data quality warnings: " + dataQualityWarnings.stream()
                .limit(6)
                .map(DataQualityWarning::message)
                .toList();
        if (dataQualityWarnings.size() > 6) {
            summary += " and " + (dataQualityWarnings.size() - 6) + " more";
        }
        return summary + ".";
    }

    private String matchedTermsSuffix(List<String> matchedTerms) {
        if (matchedTerms == null || matchedTerms.isEmpty()) {
            return "";
        }
        return " (" + String.join(", ", matchedTerms.stream().limit(3).toList()) + ")";
    }

    private boolean contradictsGeneratedFlags(
            String explanation,
            List<IngredientFlag> ingredientFlags,
            List<AdditiveFlag> additiveFlags,
            List<AllergenFlag> allergenFlags
    ) {
        if (explanation == null || explanation.isBlank()) {
            return true;
        }

        String text = explanation.toLowerCase();
        if (ingredientFlags != null && !ingredientFlags.isEmpty()
                && (text.contains("ingredient category flags were not evaluated")
                || text.contains("no ingredient category flags were detected"))) {
            return true;
        }
        if (additiveFlags != null && !additiveFlags.isEmpty()
                && (text.contains("additive observations were not fully evaluated")
                || text.contains("no additive flags were detected"))) {
            return true;
        }
        return allergenFlags != null && !allergenFlags.isEmpty()
                && (text.contains("allergen observations were not fully evaluated")
                || text.contains("no allergen flags were detected"));
    }

    private String buildPrompt(
            String productName,
            String brand,
            String category,
            String ingredientText,
            List<NutritionFlag> nutritionFlags,
            List<IngredientFlag> ingredientFlags,
            List<AdditiveFlag> additiveFlags,
            List<AllergenFlag> allergenFlags,
            List<PositiveSignal> positiveSignals,
            List<DataQualityWarning> dataQualityWarnings
    ) {
        String ingredientEvaluationStatus = ingredientEvaluationStatus(ingredientFlags, dataQualityWarnings);
        String additiveEvaluationStatus = additiveEvaluationStatus(additiveFlags, dataQualityWarnings);
        String allergenEvaluationStatus = allergenEvaluationStatus(allergenFlags, dataQualityWarnings);
        return """
                You are a neutral food quality report writer for a grocery quality review team.

                You are given a validated structured report generated by backend rules.
                Use only the provided structured flags.
                Do not create new flags.
                Do not infer allergens.
                Do not infer additives.
                Do not classify nutrition values yourself.
                Do not approve or reject the product.
                Do not give a score.
                Do not say the product is healthy or unhealthy.
                Do not make medical claims.
                Do not exaggerate.
                Do not write "none detected" for any section whose evaluation status is NOT_EVALUATED_MISSING_OR_INCOMPLETE.

                Write a clear report using exactly these section headings, in this exact order:

                Product Overview:
                [1-2 sentences about product name, brand, category, and ingredient text if provided]

                Nutrition Observations:
                [Summarize only the provided nutrition flags]

                Ingredient Observations:
                [Summarize only the provided ingredient flags.]

                Additive and Allergen Observations:
                [Summarize only the provided additive and allergen flags.]

                Data Completeness:
                [Mention provided data quality warnings. If none, say no major data completeness warnings were generated.]

                Reviewer Note:
                The final decision should be made by the reviewer based on their quality standards.

                Empty flag wording rules:
                - The explicit evaluation status fields below override generic empty-list wording.
                - Only say "none detected" when the relevant source data was available and no flags were found.
                - If Ingredient evaluation status is "NOT_EVALUATED_MISSING_OR_INCOMPLETE", write this exact sentence in Ingredient Observations and do not write "No ingredient category flags were detected":
                "Ingredient category flags were not evaluated because ingredient data was unavailable or incomplete from the source."
                - If Ingredient evaluation status is "EVALUATED_NO_FLAGS", write:
                "No ingredient category flags were detected from the available ingredient data."
                - If ingredientFlags is not empty, summarize only those provided ingredient flags.
                - If Additive evaluation status is "NOT_EVALUATED_MISSING_OR_INCOMPLETE", write this exact sentence in Additive and Allergen Observations and do not write "No additive flags were detected":
                "Additive observations were not fully evaluated because relevant source data was unavailable or incomplete."
                - If Additive evaluation status is "EVALUATED_NO_FLAGS", write:
                "No additive flags were detected from the available product data."
                - If additiveFlags is not empty, summarize only those provided additive flags.
                - If Allergen evaluation status is "NOT_EVALUATED_MISSING_OR_INCOMPLETE", write this exact sentence in Additive and Allergen Observations and do not write "No allergen flags were detected":
                "Allergen observations were not fully evaluated because allergen or trace data was unavailable or incomplete."
                - If Allergen evaluation status is "EVALUATED_NO_FLAGS", write:
                "No allergen flags were detected from the available product data."
                - If allergenFlags is not empty, summarize only those provided allergen flags.
                - When both additive and allergen observations were not fully evaluated, include both required sentences.
                - Never combine a not-evaluated sentence with a "none detected" sentence for the same section.

                Return only plain text.
                Do not return JSON.
                Do not add any facts that are not present in the input.
                The final line must be under Reviewer Note and must be exactly:
                "The final decision should be made by the reviewer based on their quality standards."

                Product:
                %s

                Brand:
                %s

                Category:
                %s

                Ingredient text:
                %s

                Ingredient evaluation status:
                %s

                Additive evaluation status:
                %s

                Allergen evaluation status:
                %s

                Nutrition flags:
                %s

                Ingredient flags:
                %s

                Additive flags:
                %s

                Allergen flags:
                %s

                Positive signals:
                %s

                Data quality warnings:
                %s
                """.formatted(
                safeText(productName),
                safeText(brand),
                safeText(category),
                safePromptText(ingredientText, 900),
                ingredientEvaluationStatus,
                additiveEvaluationStatus,
                allergenEvaluationStatus,
                toJson(compactNutritionFlags(nutritionFlags)),
                toJson(compactIngredientFlags(ingredientFlags)),
                toJson(compactAdditiveFlags(additiveFlags)),
                toJson(compactAllergenFlags(allergenFlags)),
                toJson(compactPositiveSignals(positiveSignals)),
                toJson(compactDataQualityWarnings(dataQualityWarnings))
        );
    }

    private List<Map<String, Object>> compactNutritionFlags(List<NutritionFlag> flags) {
        List<Map<String, Object>> compact = new ArrayList<>();
        if (flags == null) {
            return compact;
        }
        for (NutritionFlag flag : flags) {
            Map<String, Object> item = new LinkedHashMap<>();
            putIfPresent(item, "name", flag.name());
            putIfPresent(item, "level", flag.level());
            putIfPresent(item, "value", flag.value());
            putIfPresent(item, "explanation", flag.explanation());
            compact.add(item);
        }
        return compact;
    }

    private List<Map<String, Object>> compactIngredientFlags(List<IngredientFlag> flags) {
        List<Map<String, Object>> compact = new ArrayList<>();
        if (flags == null) {
            return compact;
        }
        for (IngredientFlag flag : flags) {
            Map<String, Object> item = new LinkedHashMap<>();
            putIfPresent(item, "category", flag.category());
            putIfPresent(item, "displayName", flag.displayName());
            putIfPresent(item, "matchedTerms", limitList(flag.matchedTerms(), 4));
            putIfPresent(item, "explanation", flag.explanation());
            compact.add(item);
        }
        return compact;
    }

    private List<Map<String, Object>> compactAdditiveFlags(List<AdditiveFlag> flags) {
        List<Map<String, Object>> compact = new ArrayList<>();
        if (flags == null) {
            return compact;
        }
        for (AdditiveFlag flag : flags) {
            Map<String, Object> item = new LinkedHashMap<>();
            putIfPresent(item, "name", flag.name());
            putIfPresent(item, "displayName", flag.displayName());
            putIfPresent(item, "classes", limitList(flag.classes(), 5));
            putIfPresent(item, "source", flag.source());
            putIfPresent(item, "explanation", flag.explanation());
            compact.add(item);
        }
        return compact;
    }

    private List<Map<String, Object>> compactAllergenFlags(List<AllergenFlag> flags) {
        List<Map<String, Object>> compact = new ArrayList<>();
        if (flags == null) {
            return compact;
        }
        for (AllergenFlag flag : flags) {
            Map<String, Object> item = new LinkedHashMap<>();
            putIfPresent(item, "name", flag.name());
            putIfPresent(item, "displayName", flag.displayName());
            putIfPresent(item, "source", flag.source());
            putIfPresent(item, "matchedTerms", limitList(flag.matchedTerms(), 4));
            putIfPresent(item, "explanation", flag.explanation());
            compact.add(item);
        }
        return compact;
    }

    private List<Map<String, Object>> compactPositiveSignals(List<PositiveSignal> signals) {
        List<Map<String, Object>> compact = new ArrayList<>();
        if (signals == null) {
            return compact;
        }
        for (PositiveSignal signal : signals) {
            Map<String, Object> item = new LinkedHashMap<>();
            putIfPresent(item, "name", signal.name());
            putIfPresent(item, "level", signal.level());
            putIfPresent(item, "value", signal.value());
            putIfPresent(item, "explanation", signal.explanation());
            compact.add(item);
        }
        return compact;
    }

    private List<Map<String, Object>> compactDataQualityWarnings(List<DataQualityWarning> warnings) {
        List<Map<String, Object>> compact = new ArrayList<>();
        if (warnings == null) {
            return compact;
        }
        for (DataQualityWarning warning : warnings) {
            Map<String, Object> item = new LinkedHashMap<>();
            putIfPresent(item, "field", warning.field());
            putIfPresent(item, "message", warning.message());
            compact.add(item);
        }
        return compact;
    }

    private List<String> limitList(List<String> values, int maxItems) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(maxItems)
                .toList();
    }

    private void putIfPresent(Map<String, Object> item, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        if (value instanceof List<?> listValue && listValue.isEmpty()) {
            return;
        }
        item.put(key, value);
    }

    private String ingredientEvaluationStatus(List<IngredientFlag> ingredientFlags, List<DataQualityWarning> dataQualityWarnings) {
        if ((ingredientFlags == null || ingredientFlags.isEmpty()) && hasDataQualityWarning(dataQualityWarnings, "ingredients")) {
            return "NOT_EVALUATED_MISSING_OR_INCOMPLETE";
        }
        if (ingredientFlags == null || ingredientFlags.isEmpty()) {
            return "EVALUATED_NO_FLAGS";
        }
        return "EVALUATED_WITH_FLAGS";
    }

    private String additiveEvaluationStatus(List<AdditiveFlag> additiveFlags, List<DataQualityWarning> dataQualityWarnings) {
        if ((additiveFlags == null || additiveFlags.isEmpty()) && hasAnyDataQualityWarning(dataQualityWarnings, List.of("ingredients", "additives", "sourceData"))) {
            return "NOT_EVALUATED_MISSING_OR_INCOMPLETE";
        }
        if (additiveFlags == null || additiveFlags.isEmpty()) {
            return "EVALUATED_NO_FLAGS";
        }
        return "EVALUATED_WITH_FLAGS";
    }

    private String allergenEvaluationStatus(List<AllergenFlag> allergenFlags, List<DataQualityWarning> dataQualityWarnings) {
        if ((allergenFlags == null || allergenFlags.isEmpty()) && hasDataQualityWarning(dataQualityWarnings, "allergens")) {
            return "NOT_EVALUATED_MISSING_OR_INCOMPLETE";
        }
        if (allergenFlags == null || allergenFlags.isEmpty()) {
            return "EVALUATED_NO_FLAGS";
        }
        return "EVALUATED_WITH_FLAGS";
    }

    private boolean hasAnyDataQualityWarning(List<DataQualityWarning> dataQualityWarnings, List<String> fields) {
        if (fields == null) {
            return false;
        }
        return fields.stream().anyMatch(field -> hasDataQualityWarning(dataQualityWarnings, field));
    }

    private boolean hasDataQualityWarning(List<DataQualityWarning> dataQualityWarnings, String field) {
        if (dataQualityWarnings == null || field == null) {
            return false;
        }
        return dataQualityWarnings.stream()
                .anyMatch(warning -> warning != null && field.equalsIgnoreCase(warning.field()));
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            return AI_UNAVAILABLE_MESSAGE;
        }
        String text = response.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText(null);
        if (text == null || text.isBlank()) {
            text = response.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText(null);
        }
        if (text == null || text.isBlank()) {
            text = response.path("choices")
                    .path(0)
                    .path("text")
                    .asText(null);
        }
        return text == null || text.isBlank() ? AI_UNAVAILABLE_MESSAGE : text;
    }

    private String normalizeExplanation(String explanation) {
        if (explanation == null || explanation.isBlank() || AI_UNAVAILABLE_MESSAGE.equals(explanation)) {
            return AI_UNAVAILABLE_MESSAGE;
        }

        String trimmed = explanation.trim();
        String reviewerNote = "Reviewer Note:\n" + REQUIRED_FINAL_SENTENCE;
        int reviewerNoteIndex = trimmed.indexOf("Reviewer Note:");
        if (reviewerNoteIndex >= 0) {
            String beforeReviewerNote = trimmed.substring(0, reviewerNoteIndex).trim();
            if (beforeReviewerNote.isBlank()) {
                return reviewerNote;
            }
            return beforeReviewerNote + "\n\n" + reviewerNote;
        }
        return trimmed + "\n\n" + reviewerNote;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "Not available" : value;
    }

    private String safePromptText(String value, int maxCharacters) {
        String text = safeText(value);
        if (text.length() <= maxCharacters) {
            return text;
        }
        return text.substring(0, maxCharacters).trim() + "...";
    }
}
