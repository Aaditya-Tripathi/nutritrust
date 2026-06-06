package com.nutritrust.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritrust.dto.AdditiveFlag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlagRuleEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FlagRuleEngine engine = new FlagRuleEngine(
            new FlagRuleLoader(objectMapper),
            new OpenFoodFactsTaxonomyLoader(objectMapper)
    );

    @Test
    void loadsJsonRulesAndTaxonomyResources() {
        FlagRuleLoader ruleLoader = new FlagRuleLoader(objectMapper);
        OpenFoodFactsTaxonomyLoader taxonomyLoader = new OpenFoodFactsTaxonomyLoader(objectMapper);

        assertThat(ruleLoader.rules())
                .extracting(FlagRule::id)
                .contains("processed-oils-fats", "sugar-thresholds", "acidity-regulators", "milk");
        assertThat(taxonomyLoader.entry("en:e330")).isNotNull();
        assertThat(taxonomyLoader.entry("en:milk")).isNotNull();
        assertThat(taxonomyLoader.allEntries())
                .filteredOn(entry -> "additives".equals(entry.taxonomy()))
                .hasSizeGreaterThan(600);
        assertThat(taxonomyLoader.allEntries())
                .filteredOn(entry -> "ingredients".equals(entry.taxonomy()))
                .hasSizeGreaterThan(4000);
        assertThat(taxonomyLoader.allEntries())
                .extracting(TaxonomyEntry::id)
                .contains("en:e330", "en:e202", "en:milk", "en:gluten", "en:palm-oil");
    }

    @Test
    void matchesIngredientTermsAliasesAndTaxonomyTags() throws Exception {
        JsonNode product = objectMapper.readTree("""
                {
                  "ingredients_tags": ["en:palm-oil"],
                  "nutriments": {}
                }
                """);

        FlagEvaluation evaluation = engine.evaluate(product, "vegetable oil", "");

        assertThat(evaluation.ingredientFlags())
                .anySatisfy(flag -> {
                    assertThat(flag.category()).isEqualTo("Processed Oils / Fats");
                    assertThat(flag.matchedTerms()).contains("palm-oil");
                });
    }

    @Test
    void detectsAdditivesFromTagsAndCommonCodeForms() throws Exception {
        JsonNode product = objectMapper.readTree("""
                {
                  "additives_tags": ["en:e330"],
                  "nutriments": {}
                }
                """);

        FlagEvaluation evaluation = engine.evaluate(product, "acidity regulator e330, E330 and INS 330", "");

        assertThat(evaluation.additiveFlags())
                .extracting(flag -> flag.name().toLowerCase())
                .contains("e330");
        assertThat(evaluation.additiveFlags())
                .anySatisfy(flag -> {
                    assertThat(flag.name()).isEqualTo("e330");
                    assertThat(flag.explanation()).contains("acidity regulation");
                    assertThat(flag.taxonomyId()).isEqualTo("en:e330");
                    assertThat(flag.displayName()).isEqualTo("Citric acid");
                    assertThat(flag.description()).isNotBlank();
                    assertThat(flag.functionDescription()).isNotBlank();
                });
    }

    @Test
    void additiveDescriptionsExplainWhatItIsAndHowItActs() throws Exception {
        JsonNode product = objectMapper.readTree("""
                {
                  "additives_tags": ["en:e322(i)", "en:e500"],
                  "nutriments": {}
                }
                """);

        FlagEvaluation evaluation = engine.evaluate(product, "", "");

        assertThat(evaluation.additiveFlags())
                .anySatisfy(flag -> {
                    assertThat(flag.taxonomyId()).isEqualTo("en:e322(i)");
                    assertThat(flag.displayName()).isEqualTo("Lecithin");
                    assertThat(flag.description()).contains("food additive");
                    assertThat(flag.functionDescription()).contains("oil and water");
                })
                .anySatisfy(flag -> {
                    assertThat(flag.taxonomyId()).isEqualTo("en:e500");
                    assertThat(flag.displayName()).isEqualTo("Sodium carbonates");
                    assertThat(flag.description()).contains("SODIUM CARBONATES");
                    assertThat(flag.functionDescription()).contains("acidity").contains("rise");
                });
    }

    @Test
    void detectsAllergensFromAllergenFieldsAndIngredientText() throws Exception {
        JsonNode product = objectMapper.readTree("""
                {
                  "allergens_tags": ["en:milk"],
                  "traces_tags": ["en:peanuts"],
                  "nutriments": {}
                }
                """);

        FlagEvaluation evaluation = engine.evaluate(product, "contains wheat flour and whey powder", "en:milk en:peanuts");

        assertThat(evaluation.allergenFlags())
                .extracting(flag -> flag.name())
                .contains("milk", "peanuts", "gluten");
        assertThat(evaluation.allergenFlags())
                .anySatisfy(flag -> {
                    assertThat(flag.name()).isEqualTo("gluten");
                    assertThat(flag.source()).isEqualTo("ingredients_text");
                });
    }

    @Test
    void appliesNutritionThresholdRulesFromJson() throws Exception {
        JsonNode product = objectMapper.readTree("""
                {
                  "nutriments": {
                    "sugars_100g": 23,
                    "sodium_100g": 0.7,
                    "saturated-fat_100g": 1,
                    "proteins_100g": 9,
                    "fiber_100g": 7
                  }
                }
                """);

        FlagEvaluation evaluation = engine.evaluate(product, "", "");

        assertThat(evaluation.nutritionFlags())
                .anySatisfy(flag -> {
                    assertThat(flag.name()).isEqualTo("Sugar");
                    assertThat(flag.level()).isEqualTo("HIGH");
                    assertThat(flag.description()).contains("total sugars");
                    assertThat(flag.functionDescription()).contains("per-100g thresholds");
                })
                .anySatisfy(flag -> {
                    assertThat(flag.name()).isEqualTo("Salt");
                    assertThat(flag.value()).isEqualTo("1.75g per 100g");
                    assertThat(flag.explanation()).contains("calculated from sodium");
                });
        assertThat(evaluation.positiveSignals())
                .extracting(signal -> signal.name())
                .contains("Protein", "Fiber");
    }

    @Test
    void reportsFavorableLowNutritionLevelsAsPositiveSignals() throws Exception {
        JsonNode product = objectMapper.readTree("""
                {
                  "nutriments": {
                    "energy-kcal_100g": 120,
                    "fat_100g": 2,
                    "sugars_100g": 3,
                    "sodium_100g": 0.05,
                    "saturated-fat_100g": 0.5,
                    "trans-fat_100g": 0,
                    "cholesterol_100g": 0,
                    "proteins_100g": 1,
                    "fiber_100g": 1
                  }
                }
                """);

        FlagEvaluation evaluation = engine.evaluate(product, "", "");

        assertThat(evaluation.positiveSignals())
                .extracting(signal -> signal.name())
                .contains(
                        "Energy",
                        "Total fat",
                        "Sugar",
                        "Salt",
                        "Sodium",
                        "Saturated fat",
                        "Trans fat",
                        "Cholesterol"
                )
                .doesNotContain("Protein", "Fiber");
    }

    @Test
    void usesPreparedPer100gNutritionValuesForPositiveSignals() throws Exception {
        JsonNode product = objectMapper.readTree("""
                {
                  "nutriments": {
                    "sugars_prepared_100g": 4,
                    "salt_prepared_100g": 0.2,
                    "saturated-fat_prepared_100g": 1,
                    "proteins_prepared_100g": 9,
                    "fiber_prepared_100g": 7
                  }
                }
                """);

        FlagEvaluation evaluation = engine.evaluate(product, "", "");

        assertThat(evaluation.positiveSignals())
                .extracting(signal -> signal.name())
                .contains("Sugar", "Salt", "Saturated fat", "Protein", "Fiber");
    }

    @Test
    void deserializesOldSavedAdditiveFlagsWithoutMetadata() throws Exception {
        AdditiveFlag[] flags = objectMapper.readValue("""
                [
                  {
                    "name": "e330",
                    "source": "additives_tags",
                    "explanation": "This additive code is listed in the product data."
                  }
                ]
                """, AdditiveFlag[].class);

        assertThat(flags).hasSize(1);
        assertThat(flags[0].name()).isEqualTo("e330");
        assertThat(flags[0].taxonomyId()).isNull();
    }
}
