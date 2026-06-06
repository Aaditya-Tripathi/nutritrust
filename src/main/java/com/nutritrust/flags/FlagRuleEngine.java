package com.nutritrust.flags;

import com.fasterxml.jackson.databind.JsonNode;
import com.nutritrust.dto.AdditiveFlag;
import com.nutritrust.dto.AllergenFlag;
import com.nutritrust.dto.IngredientFlag;
import com.nutritrust.dto.NutritionFlag;
import com.nutritrust.dto.PositiveSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FlagRuleEngine {

    private static final Pattern ADDITIVE_CODE_PATTERN = Pattern.compile(
            "(?i)\\b(?:INS\\s*\\d{3,4}[a-z]?(?:\\s*\\([ivx]+\\))?|E\\s*\\d{3,4}[a-z]?(?:\\s*\\([ivx]+\\))?)(?![a-z0-9])"
    );
    private static final Pattern ADDITIVE_ID_PATTERN = Pattern.compile("^en:e\\d{3,4}[a-z]?(?:\\([ivx]+\\))?$");
    private static final int MAX_MATCHED_TERMS = 12;
    private static final int MAX_NGRAM_WORDS = 8;
    private static final String ADDITIVE_EXPLANATION = "This additive is listed in the product data.";
    private static final String ALLERGEN_EXPLANATION = "This allergen or trace allergen is listed in the product data.";

    private final List<FlagRule> rules;
    private final Map<String, TaxonomyEntry> entriesById;
    private final Map<String, List<TaxonomyEntry>> entriesByTerm;
    private final Map<String, TaxonomyEntry> additivesByCode;
    private final Map<String, TaxonomyEntry> additiveClassEntries;
    private final Map<String, TaxonomyEntry> allergenEntries;
    private final Map<String, TaxonomyEntry> ingredientEntries;

    public FlagRuleEngine(FlagRuleLoader flagRuleLoader, OpenFoodFactsTaxonomyLoader taxonomyLoader) {
        this.rules = flagRuleLoader.rules();
        this.entriesById = taxonomyLoader.entriesById();
        this.additiveClassEntries = filterByTaxonomy(taxonomyLoader.allEntries(), "additive-classes");
        this.allergenEntries = filterByTaxonomy(taxonomyLoader.allEntries(), "allergens");
        this.ingredientEntries = filterByTaxonomy(taxonomyLoader.allEntries(), "ingredients");
        Map<String, TaxonomyEntry> additiveEntries = filterByTaxonomy(taxonomyLoader.allEntries(), "additives");
        this.additivesByCode = indexAdditivesByCode(additiveEntries.values());
        this.entriesByTerm = indexEntriesByTerm(taxonomyLoader.allEntries());
    }

    public FlagEvaluation evaluate(JsonNode product, String combinedIngredientText, String combinedAllergenText) {
        JsonNode nutriments = product == null ? null : product.path("nutriments");
        List<NutritionFlag> nutritionFlags = buildNutritionFlags(nutriments);
        Set<TaxonomyEntry> matchedIngredients = matchedEntries(
                combinedIngredientText,
                normalizedTags(product, List.of("ingredients_tags", "ingredients_original_tags")),
                "ingredients"
        );

        return new FlagEvaluation(
                nutritionFlags,
                buildIngredientFlags(product, combinedIngredientText, matchedIngredients),
                buildAdditiveFlags(product, combinedIngredientText),
                buildAllergenFlags(product, combinedIngredientText, combinedAllergenText, matchedIngredients),
                buildPositiveSignals(nutriments, nutritionFlags)
        );
    }

    private List<NutritionFlag> buildNutritionFlags(JsonNode nutriments) {
        if (nutriments == null || nutriments.isMissingNode() || nutriments.isNull()) {
            return List.of();
        }

        List<NutritionFlag> flags = new ArrayList<>();
        for (FlagRule rule : enabledRules(FlagType.NUTRITION)) {
            NutritionValue value = resolveNutritionValue(nutriments, rule);
            if (value.value() == null) {
                continue;
            }

            String level = levelFor(value.value(), rule.levels());
            if (level == null) {
                continue;
            }

            String name = textOrDefault(rule.nutrientName(), rule.category());
            String explanation = name + " is " + level.toLowerCase(Locale.ROOT) + " based on the selected threshold.";
            if (value.calculatedFromFallback()) {
                explanation += " " + textOrDefault(rule.fallbackExplanation(), "Value was calculated from a related source field.");
            }
            flags.add(new NutritionFlag(
                    name,
                    level,
                    formattedNutritionValue(value.value(), rule.unit()),
                    explanation,
                    textOrDefault(rule.description(), nutritionDescription(name)),
                    textOrDefault(rule.functionDescription(), nutritionAction(name)),
                    nullToEmpty(rule.sourceUrls())
            ));
        }
        return flags;
    }

    private NutritionValue resolveNutritionValue(JsonNode nutriments, FlagRule rule) {
        Double directValue = firstNutritionValue(nutriments, rule.nutrientFields());
        if (directValue != null) {
            return new NutritionValue(directValue, false);
        }

        Double fallbackValue = firstNutritionValue(nutriments, rule.fallbackNutrientFields());
        if (fallbackValue == null) {
            return new NutritionValue(null, false);
        }
        double multiplier = rule.fallbackMultiplier() == null ? 1.0 : rule.fallbackMultiplier();
        return new NutritionValue(fallbackValue * multiplier, true);
    }

    private Double firstNutritionValue(JsonNode nutriments, List<String> fields) {
        if (fields == null) {
            return null;
        }
        for (String field : fields) {
            for (String candidate : nutritionFieldCandidates(field)) {
                Double value = doubleOrNull(nutriments.path(candidate));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private List<String> nutritionFieldCandidates(String field) {
        String cleaned = cleanText(field);
        if (cleaned == null) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(cleaned);
        if (cleaned.endsWith("_100g")) {
            candidates.add(cleaned.substring(0, cleaned.length() - "_100g".length()) + "_prepared_100g");
        }
        return new ArrayList<>(candidates);
    }

    private String levelFor(Double value, List<NutritionLevelDefinition> levels) {
        if (value == null || levels == null) {
            return null;
        }
        for (NutritionLevelDefinition level : levels) {
            boolean aboveMin = level.minInclusive() == null || value >= level.minInclusive();
            boolean belowMax = level.maxInclusive() == null || value <= level.maxInclusive();
            if (aboveMin && belowMax) {
                return level.level();
            }
        }
        return null;
    }

    private List<IngredientFlag> buildIngredientFlags(JsonNode product, String combinedIngredientText, Set<TaxonomyEntry> matchedIngredients) {
        Map<String, IngredientAccumulator> flags = new LinkedHashMap<>();
        addConfiguredIngredientRules(flags, product, combinedIngredientText);
        addTaxonomyIngredientFlags(flags, matchedIngredients);
        return flags.values().stream()
                .map(IngredientAccumulator::toFlag)
                .toList();
    }

    private void addConfiguredIngredientRules(Map<String, IngredientAccumulator> flags, JsonNode product, String combinedIngredientText) {
        String normalizedIngredientText = normalizeText(combinedIngredientText);
        Set<String> ingredientTags = normalizedTags(product, List.of("ingredients_tags", "ingredients_original_tags"));
        if (normalizedIngredientText.isBlank() && ingredientTags.isEmpty()) {
            return;
        }

        for (FlagRule rule : enabledRules(FlagType.INGREDIENT)) {
            List<String> matchedTerms = matchedRuleTerms(rule, normalizedIngredientText, ingredientTags);
            if (!matchedTerms.isEmpty()) {
                IngredientAccumulator accumulator = flags.computeIfAbsent(
                        rule.id(),
                        key -> new IngredientAccumulator(
                                rule.category(),
                                rule.id(),
                                rule.category(),
                                List.of(),
                                rule.explanation(),
                                textOrDefault(rule.description(), rule.category() + " is a configured ingredient review category."),
                                textOrDefault(rule.functionDescription(), rule.explanation()),
                                nullToEmpty(rule.sourceUrls())
                        )
                );
                accumulator.addTerms(matchedTerms);
                accumulator.addTaxonomyIds(rule.taxonomyIds());
            }
        }
    }

    private void addTaxonomyIngredientFlags(Map<String, IngredientAccumulator> flags, Set<TaxonomyEntry> matchedIngredients) {
        for (TaxonomyEntry ingredient : matchedIngredients) {
            String matchedName = displayName(ingredient);
            for (String parentId : ancestryIds(ingredient)) {
                TaxonomyEntry parent = ingredientEntries.get(parentId);
                if (parent == null || parent.id().equals(ingredient.id())) {
                    continue;
                }
                String category = "Ingredient Family: " + displayName(parent);
                IngredientAccumulator accumulator = flags.computeIfAbsent(
                        "ingredient-family:" + parent.id(),
                        key -> new IngredientAccumulator(
                                category,
                                parent.id(),
                                displayName(parent),
                                List.of(),
                                "The ingredient list contains entries from this Open Food Facts ingredient family.",
                                descriptionForIngredient(parent),
                                "NutriTrust groups matched ingredients under this Open Food Facts parent family so reviewers can see the broader ingredient type represented on the label.",
                                sourceUrls(parent)
                        )
                );
                accumulator.addTerm(matchedName);
                accumulator.addTaxonomyId(ingredient.id());
            }

            for (String classId : nullToEmpty(ingredient.classes())) {
                String category = "Ingredient Additive Class: " + displayForId(classId);
                IngredientAccumulator accumulator = flags.computeIfAbsent(
                        "ingredient-class:" + classId,
                        key -> new IngredientAccumulator(
                                category,
                                classId,
                                displayForId(classId),
                                List.of(displayForId(classId)),
                                "The ingredient list contains entries associated with this additive function class.",
                                descriptionForClass(classId),
                                actionForClass(classId),
                                sourceUrls(entriesById.get(canonicalTag(classId)))
                        )
                );
                accumulator.addTerm(matchedName);
                accumulator.addTaxonomyId(ingredient.id());
            }

            if ("yes".equalsIgnoreCase(ingredient.fromPalmOil()) || "maybe".equalsIgnoreCase(ingredient.fromPalmOil())) {
                IngredientAccumulator accumulator = flags.computeIfAbsent(
                        "ingredient-property:palm-oil",
                        key -> new IngredientAccumulator(
                                "Palm Oil / Palm-Derived Ingredients",
                                "from_palm_oil",
                                "Palm oil / palm-derived",
                                List.of(),
                                "The ingredient taxonomy marks one or more matched ingredients as palm oil-derived or possibly palm oil-derived.",
                                "This flag means one or more matched ingredients are marked by Open Food Facts as palm oil-derived or possibly palm oil-derived.",
                                "NutriTrust uses the taxonomy property to group palm-derived ingredients even when the label uses a more specific ingredient name.",
                                List.of()
                        )
                );
                accumulator.addTerm(matchedName);
                accumulator.addTaxonomyId(ingredient.id());
            }

            if (ingredient.nova() != null && !ingredient.nova().isBlank()) {
                IngredientAccumulator accumulator = flags.computeIfAbsent(
                        "ingredient-property:nova-" + ingredient.nova(),
                        key -> new IngredientAccumulator(
                                "NOVA " + ingredient.nova() + " Marker Ingredients",
                                "nova:" + ingredient.nova(),
                                "NOVA " + ingredient.nova(),
                                List.of(),
                                "The ingredient taxonomy marks one or more matched ingredients as NOVA markers.",
                                "This flag means one or more matched ingredients carry a NOVA marker in the Open Food Facts ingredient taxonomy.",
                                "NutriTrust reports the marker as taxonomy evidence only; it does not calculate or assign an overall NOVA score for the product.",
                                List.of()
                        )
                );
                accumulator.addTerm(matchedName);
                accumulator.addTaxonomyId(ingredient.id());
            }

            if ("no".equalsIgnoreCase(ingredient.vegan()) || "maybe".equalsIgnoreCase(ingredient.vegan())) {
                String key = "ingredient-property:vegan-" + ingredient.vegan().toLowerCase(Locale.ROOT);
                IngredientAccumulator accumulator = flags.computeIfAbsent(
                        key,
                        ignored -> new IngredientAccumulator(
                                "Vegan Status: " + ingredient.vegan().toUpperCase(Locale.ROOT),
                                "vegan:" + ingredient.vegan(),
                                "Vegan " + ingredient.vegan(),
                                List.of(),
                                "The ingredient taxonomy marks one or more matched ingredients with this vegan suitability status.",
                                "This flag groups ingredients whose Open Food Facts vegan property is " + ingredient.vegan() + ".",
                                "NutriTrust reports the taxonomy status so reviewers can inspect suitability evidence without treating it as an approval or rejection.",
                                List.of()
                        )
                );
                accumulator.addTerm(matchedName);
                accumulator.addTaxonomyId(ingredient.id());
            }

            if ("no".equalsIgnoreCase(ingredient.vegetarian()) || "maybe".equalsIgnoreCase(ingredient.vegetarian())) {
                String key = "ingredient-property:vegetarian-" + ingredient.vegetarian().toLowerCase(Locale.ROOT);
                IngredientAccumulator accumulator = flags.computeIfAbsent(
                        key,
                        ignored -> new IngredientAccumulator(
                                "Vegetarian Status: " + ingredient.vegetarian().toUpperCase(Locale.ROOT),
                                "vegetarian:" + ingredient.vegetarian(),
                                "Vegetarian " + ingredient.vegetarian(),
                                List.of(),
                                "The ingredient taxonomy marks one or more matched ingredients with this vegetarian suitability status.",
                                "This flag groups ingredients whose Open Food Facts vegetarian property is " + ingredient.vegetarian() + ".",
                                "NutriTrust reports the taxonomy status so reviewers can inspect suitability evidence without treating it as an approval or rejection.",
                                List.of()
                        )
                );
                accumulator.addTerm(matchedName);
                accumulator.addTaxonomyId(ingredient.id());
            }
        }
    }

    private List<AdditiveFlag> buildAdditiveFlags(JsonNode product, String combinedIngredientText) {
        Map<String, AdditiveAccumulator> flags = new LinkedHashMap<>();
        for (String field : List.of("additives_tags", "additives_original_tags", "additives_old_tags")) {
            for (String value : textValues(path(product, field))) {
                TaxonomyEntry entry = additiveEntryFor(value);
                if (entry != null) {
                    putAdditive(flags, entry, field, value);
                } else {
                    putUnknownAdditive(flags, cleanTag(value), field);
                }
            }
        }

        for (String code : extractAdditiveCodes(combinedIngredientText)) {
            TaxonomyEntry entry = additiveEntryFor(code);
            if (entry != null) {
                putAdditive(flags, entry, "ingredients_text", code);
            } else {
                putUnknownAdditive(flags, code, "ingredients_text");
            }
        }

        for (TaxonomyEntry entry : matchedEntries(combinedIngredientText, Set.of(), "additives")) {
            putAdditive(flags, entry, "ingredients_text", displayName(entry));
        }

        return flags.values().stream()
                .map(AdditiveAccumulator::toFlag)
                .toList();
    }

    private void putAdditive(Map<String, AdditiveAccumulator> flags, TaxonomyEntry entry, String source, String matchedTerm) {
        String key = entry.id();
        AdditiveAccumulator accumulator = flags.computeIfAbsent(
                key,
                ignored -> new AdditiveAccumulator(
                        additiveName(entry),
                        source,
                        explanationForAdditive(entry),
                        entry.id(),
                        commonName(entry),
                        displayClasses(entry.classes()),
                        descriptionForAdditive(entry),
                        actionForAdditive(entry),
                        sourceUrls(entry)
                )
        );
        accumulator.addSource(source);
        accumulator.addMatchedTerm(matchedTerm);
        accumulator.addTaxonomyId(entry.id());
    }

    private void putUnknownAdditive(Map<String, AdditiveAccumulator> flags, String name, String source) {
        String cleaned = cleanText(name);
        if (cleaned == null) {
            return;
        }
        String key = normalizeAdditiveKey(cleaned);
        AdditiveAccumulator accumulator = flags.computeIfAbsent(
                key,
                ignored -> new AdditiveAccumulator(
                        cleaned,
                        source,
                        ADDITIVE_EXPLANATION,
                        null,
                        null,
                        List.of(),
                        "This additive code or name was detected in the available product data, but NutriTrust could not match it to a generated Open Food Facts taxonomy entry.",
                        "NutriTrust reports the additive presence factually from the source field; no additional function class was available.",
                        List.of()
                )
        );
        accumulator.addMatchedTerm(cleaned);
    }

    private String explanationForAdditive(TaxonomyEntry additive) {
        List<String> classes = displayClasses(additive.classes());
        for (FlagRule rule : enabledRules(FlagType.ADDITIVE)) {
            if (containsIgnoreCase(rule.taxonomyIds(), additive.id()) || overlapsIgnoreCase(rule.taxonomyClassIds(), additive.classes())) {
                if (classes.isEmpty()) {
                    return rule.explanation();
                }
                return rule.explanation() + " Taxonomy classes: " + String.join(", ", classes) + ".";
            }
        }
        if (!classes.isEmpty()) {
            return "This additive is listed in the product data and is associated with: " + String.join(", ", classes) + ".";
        }
        return ADDITIVE_EXPLANATION;
    }

    private List<AllergenFlag> buildAllergenFlags(
            JsonNode product,
            String combinedIngredientText,
            String combinedAllergenText,
            Set<TaxonomyEntry> matchedIngredients
    ) {
        Map<String, AllergenAccumulator> flags = new LinkedHashMap<>();
        addAllergenMatches(flags, combinedAllergenText, normalizedTags(product, List.of("allergens_tags", "traces_tags")), "allergen_fields");
        addAllergenMatches(flags, combinedIngredientText, Set.of(), "ingredients_text");

        for (TaxonomyEntry ingredient : matchedIngredients) {
            for (String allergenId : nullToEmpty(ingredient.allergens())) {
                TaxonomyEntry allergen = allergenEntries.get(allergenId);
                if (allergen != null) {
                    putAllergen(flags, allergen, "ingredients_text", displayName(ingredient));
                }
            }
        }

        return flags.values().stream()
                .map(AllergenAccumulator::toFlag)
                .toList();
    }

    private void addAllergenMatches(Map<String, AllergenAccumulator> flags, String text, Set<String> tags, String source) {
        for (TaxonomyEntry allergen : matchedEntries(text, tags, "allergens")) {
            if (!"en:none".equals(allergen.id())) {
                putAllergen(flags, allergen, source, displayName(allergen));
            }
        }
    }

    private void putAllergen(Map<String, AllergenAccumulator> flags, TaxonomyEntry allergen, String source, String matchedTerm) {
        AllergenAccumulator accumulator = flags.computeIfAbsent(
                allergen.id(),
                ignored -> new AllergenAccumulator(
                        displayName(allergen),
                        source,
                        ALLERGEN_EXPLANATION,
                        allergen.id(),
                        displayName(allergen),
                        List.of(),
                        descriptionForAllergen(allergen),
                        actionForAllergen(allergen),
                        sourceUrls(allergen)
                )
        );
        accumulator.addSource(source);
        accumulator.addMatchedTerm(matchedTerm);
        accumulator.addTaxonomyId(allergen.id());
    }

    private List<PositiveSignal> buildPositiveSignals(JsonNode nutriments, List<NutritionFlag> nutritionFlags) {
        if (nutriments == null || nutriments.isMissingNode() || nutriments.isNull()) {
            return List.of();
        }

        Map<String, NutritionFlag> flagsByName = new LinkedHashMap<>();
        for (NutritionFlag flag : nutritionFlags) {
            flagsByName.put(normalizeText(flag.name()), flag);
        }

        List<PositiveSignal> signals = new ArrayList<>();
        for (FlagRule rule : enabledRules(FlagType.NUTRITION)) {
            String name = textOrDefault(rule.nutrientName(), rule.category());
            NutritionFlag flag = flagsByName.get(normalizeText(name));
            if (flag != null && containsIgnoreCase(rule.positiveSignalLevels(), flag.level())) {
                signals.add(new PositiveSignal(name, flag.level(), flag.value(), name + " is " + flag.level().toLowerCase(Locale.ROOT) + " based on the selected threshold."));
            }
        }
        return signals;
    }

    private String nutritionDescription(String name) {
        String normalized = normalizeText(name);
        return switch (normalized) {
            case "energy" -> "Energy is the amount of calories reported for the product, usually shown as kcal or kJ per 100g.";
            case "total fat" -> "Total fat is the overall fat content reported in the structured nutrition data.";
            case "sugar" -> "Sugar is the total sugars value reported in the nutrition table, including naturally occurring and added sugars where the source does not separate them.";
            case "added sugars" -> "Added sugars are sugars reported as added during formulation when the source product data provides that field.";
            case "salt" -> "Salt is the salt value reported by the product source, or calculated from sodium when salt is missing.";
            case "sodium" -> "Sodium is the sodium value reported in the structured nutrition data.";
            case "saturated fat" -> "Saturated fat is the portion of fat reported as saturated fatty acids.";
            case "trans fat" -> "Trans fat is the trans fatty acid value reported in the structured nutrition data when available.";
            case "cholesterol" -> "Cholesterol is the cholesterol value reported in the structured nutrition data when available.";
            case "carbohydrates" -> "Carbohydrates are the total carbohydrate value reported in the nutrition table.";
            case "protein" -> "Protein is the protein value reported in the structured nutrition data.";
            case "fiber" -> "Fiber is the dietary fiber value reported in the structured nutrition data.";
            default -> name + " is a nutrition value reported in the structured product data.";
        };
    }

    private String nutritionAction(String name) {
        String normalized = normalizeText(name);
        return switch (normalized) {
            case "protein", "fiber" -> "NutriTrust compares this value against configured per-100g thresholds and can report it as a positive signal when it reaches the configured good range.";
            default -> "NutriTrust compares this value against configured per-100g thresholds and reports the resulting level factually.";
        };
    }

    private Set<TaxonomyEntry> matchedEntries(String text, Set<String> tags, String taxonomy) {
        Map<String, TaxonomyEntry> matches = new LinkedHashMap<>();
        for (String tag : tags) {
            TaxonomyEntry entry = entryForTag(tag, taxonomy);
            if (entry != null) {
                matches.putIfAbsent(entry.id(), entry);
            }
        }

        for (String ngram : ngrams(normalizeText(text))) {
            for (TaxonomyEntry entry : nullToEmptyEntries(entriesByTerm.get(ngram))) {
                if (taxonomy.equals(entry.taxonomy())) {
                    matches.putIfAbsent(entry.id(), entry);
                }
            }
        }
        return new LinkedHashSet<>(matches.values());
    }

    private TaxonomyEntry entryForTag(String tag, String taxonomy) {
        String normalized = canonicalTag(tag);
        TaxonomyEntry direct = switch (taxonomy) {
            case "additives" -> additiveEntryFor(normalized);
            case "allergens" -> allergenEntries.get(normalized);
            case "ingredients" -> ingredientEntries.get(normalized);
            default -> entriesById.get(normalized);
        };
        if (direct != null) {
            return direct;
        }
        return entriesById.get(normalized);
    }

    private TaxonomyEntry additiveEntryFor(String value) {
        String normalizedId = canonicalTag(value);
        TaxonomyEntry byId = entriesById.get(normalizedId);
        if (byId != null && "additives".equals(byId.taxonomy())) {
            return byId;
        }
        String code = normalizeAdditiveId(value);
        return code == null ? null : additivesByCode.get(code);
    }

    private List<String> matchedRuleTerms(FlagRule rule, String normalizedText, Set<String> normalizedTags) {
        List<String> matchedTerms = new ArrayList<>();
        for (String term : expandedTerms(rule)) {
            String normalizedTerm = normalizeText(term);
            if (!normalizedTerm.isBlank() && containsNormalizedTerm(normalizedText, normalizedTerm)) {
                matchedTerms.add(term);
            }
        }

        for (String taxonomyId : nullToEmpty(rule.taxonomyIds())) {
            String normalizedTaxonomyId = taxonomyId.toLowerCase(Locale.ROOT);
            String normalizedCleanTag = normalizeText(cleanTag(taxonomyId));
            if (normalizedTags.contains(normalizedTaxonomyId) || normalizedTags.contains(normalizedCleanTag)) {
                matchedTerms.add(cleanTag(taxonomyId));
            }
        }
        return capped(deduplicate(matchedTerms));
    }

    private List<String> expandedTerms(FlagRule rule) {
        List<String> terms = new ArrayList<>(nullToEmpty(rule.terms()));
        for (String taxonomyId : nullToEmpty(rule.taxonomyIds())) {
            TaxonomyEntry entry = entriesById.get(canonicalTag(taxonomyId));
            if (entry != null) {
                terms.addAll(nullToEmpty(entry.names()));
                terms.addAll(nullToEmpty(entry.aliases()));
                terms.addAll(nullToEmpty(entry.codes()));
            }
        }
        return deduplicate(terms);
    }

    private Set<String> ancestryIds(TaxonomyEntry entry) {
        Set<String> ids = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(nullToEmpty(entry.parents()));
        while (!queue.isEmpty()) {
            String parentId = queue.removeFirst();
            if (!ids.add(parentId)) {
                continue;
            }
            TaxonomyEntry parent = ingredientEntries.get(parentId);
            if (parent != null) {
                queue.addAll(nullToEmpty(parent.parents()));
            }
        }
        return ids;
    }

    private Map<String, TaxonomyEntry> filterByTaxonomy(List<TaxonomyEntry> entries, String taxonomy) {
        Map<String, TaxonomyEntry> filtered = new LinkedHashMap<>();
        entries.stream()
                .filter(entry -> taxonomy.equals(entry.taxonomy()))
                .sorted(Comparator.comparing(TaxonomyEntry::id))
                .forEach(entry -> filtered.putIfAbsent(entry.id(), entry));
        return Map.copyOf(filtered);
    }

    private Map<String, TaxonomyEntry> indexAdditivesByCode(Iterable<TaxonomyEntry> additives) {
        Map<String, TaxonomyEntry> indexed = new LinkedHashMap<>();
        for (TaxonomyEntry additive : additives) {
            String idCode = normalizeAdditiveId(additive.id());
            if (idCode != null) {
                indexed.putIfAbsent(idCode, additive);
            }
            for (String code : nullToEmpty(additive.codes())) {
                String normalizedCode = normalizeAdditiveId(code);
                if (normalizedCode != null) {
                    indexed.putIfAbsent(normalizedCode, additive);
                }
            }
        }
        return Map.copyOf(indexed);
    }

    private Map<String, List<TaxonomyEntry>> indexEntriesByTerm(List<TaxonomyEntry> entries) {
        Map<String, List<TaxonomyEntry>> indexed = new LinkedHashMap<>();
        for (TaxonomyEntry entry : entries) {
            List<String> terms = new ArrayList<>();
            terms.addAll(nullToEmpty(entry.names()));
            terms.addAll(nullToEmpty(entry.aliases()));
            terms.addAll(nullToEmpty(entry.codes()));
            for (String term : terms) {
                String normalized = normalizeText(term);
                if (isIndexableTerm(normalized)) {
                    indexed.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(entry);
                }
            }
        }
        indexed.replaceAll((key, values) -> List.copyOf(values));
        return Map.copyOf(indexed);
    }

    private boolean isIndexableTerm(String normalized) {
        return normalized.length() >= 3
                && !normalized.matches("\\d+")
                && normalized.split("\\s+").length <= MAX_NGRAM_WORDS;
    }

    private List<String> ngrams(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return List.of();
        }
        String[] words = normalizedText.split("\\s+");
        List<String> values = new ArrayList<>();
        for (int start = 0; start < words.length; start++) {
            StringBuilder builder = new StringBuilder();
            for (int length = 1; length <= MAX_NGRAM_WORDS && start + length <= words.length; length++) {
                if (length > 1) {
                    builder.append(' ');
                }
                builder.append(words[start + length - 1]);
                values.add(builder.toString());
            }
        }
        return values;
    }

    private List<FlagRule> enabledRules(FlagType type) {
        return rules.stream()
                .filter(FlagRule::isEnabled)
                .filter(rule -> rule.type() == type)
                .toList();
    }

    private Set<String> normalizedTags(JsonNode product, List<String> fields) {
        Set<String> tags = new LinkedHashSet<>();
        for (String field : fields) {
            for (String value : textValues(path(product, field))) {
                String cleaned = cleanText(value);
                if (cleaned == null) {
                    continue;
                }
                tags.add(canonicalTag(cleaned));
                tags.add(normalizeText(cleanTag(cleaned)));
            }
        }
        return tags;
    }

    private List<String> extractAdditiveCodes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> codes = new ArrayList<>();
        Matcher matcher = ADDITIVE_CODE_PATTERN.matcher(text);
        while (matcher.find()) {
            codes.add(normalizeAdditiveCode(matcher.group()));
        }
        return deduplicate(codes);
    }

    private JsonNode path(JsonNode node, String field) {
        return node == null ? null : node.path(field);
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

    private boolean containsNormalizedTerm(String normalizedText, String normalizedTerm) {
        if (normalizedText == null || normalizedText.isBlank() || normalizedTerm == null || normalizedTerm.isBlank()) {
            return false;
        }
        Pattern termPattern = Pattern.compile("(^|\\s)" + Pattern.quote(normalizedTerm) + "(\\s|$)");
        return termPattern.matcher(normalizedText).find();
    }

    private boolean containsIgnoreCase(List<String> values, String needle) {
        if (values == null || needle == null) {
            return false;
        }
        return values.stream().anyMatch(value -> needle.equalsIgnoreCase(value));
    }

    private boolean overlapsIgnoreCase(List<String> first, List<String> second) {
        if (first == null || second == null) {
            return false;
        }
        return first.stream().anyMatch(value -> containsIgnoreCase(second, value));
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<TaxonomyEntry> nullToEmptyEntries(List<TaxonomyEntry> values) {
        return values == null ? List.of() : values;
    }

    private List<String> capped(List<String> values) {
        return values.size() <= MAX_MATCHED_TERMS ? values : values.subList(0, MAX_MATCHED_TERMS);
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

    private String canonicalTag(String value) {
        String cleaned = cleanText(value);
        if (cleaned == null) {
            return "";
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.startsWith("en:")) {
            String rest = lower.substring(3);
            if (rest.matches("e\\s*\\d{3,4}[a-z]?(?:\\s*\\([ivx]+\\))?")) {
                return "en:" + rest.replaceAll("\\s+", "");
            }
            return "en:" + rest.replace('_', '-');
        }
        String additiveId = normalizeAdditiveId(cleaned);
        if (additiveId != null) {
            return "en:" + additiveId;
        }
        return "en:" + normalizeText(cleaned).replace(' ', '-');
    }

    private String normalizeAdditiveCode(String code) {
        String cleaned = code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (cleaned.startsWith("INS")) {
            return "E" + cleaned.substring(3);
        }
        return cleaned;
    }

    private String normalizeAdditiveId(String code) {
        String cleaned = cleanTag(textOrDefault(code, "")).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("ins")) {
            return "e" + cleaned.substring(3);
        }
        if (cleaned.startsWith("e") && ADDITIVE_ID_PATTERN.matcher("en:" + cleaned).matches()) {
            return cleaned;
        }
        return null;
    }

    private String normalizeAdditiveKey(String code) {
        String cleaned = code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (cleaned.matches("INS\\d{3,4}.*")) {
            return cleaned.substring(3);
        }
        if (cleaned.matches("E\\d{3,4}.*")) {
            return cleaned.substring(1);
        }
        return cleaned;
    }

    private String additiveName(TaxonomyEntry entry) {
        return nullToEmpty(entry.codes()).stream().findFirst().map(String::toLowerCase).orElse(entry.id().substring(3));
    }

    private String displayName(TaxonomyEntry entry) {
        return nullToEmpty(entry.names()).stream().findFirst().orElse(cleanTag(entry.id()));
    }

    private String commonName(TaxonomyEntry entry) {
        return nullToEmpty(entry.aliases()).stream()
                .filter(alias -> normalizeAdditiveId(alias) == null)
                .findFirst()
                .orElse(displayName(entry));
    }

    private String displayForId(String id) {
        TaxonomyEntry entry = entriesById.get(canonicalTag(id));
        return entry == null ? cleanTag(id) : displayName(entry);
    }

    private List<String> displayClasses(List<String> classIds) {
        List<String> classes = new ArrayList<>();
        for (String classId : nullToEmpty(classIds)) {
            TaxonomyEntry classEntry = additiveClassEntries.get(classId);
            classes.add(classEntry == null ? cleanTag(classId) : displayName(classEntry));
        }
        return deduplicate(classes);
    }

    private String descriptionForAdditive(TaxonomyEntry additive) {
        String description = cleanText(additive.description());
        if (description != null) {
            return description;
        }

        String code = additiveName(additive).toUpperCase(Locale.ROOT);
        String common = commonName(additive);
        if (!common.equalsIgnoreCase(code)) {
            return common + " is a food additive identified by the code " + code + " in the Open Food Facts additive taxonomy.";
        }
        return code + " is a food additive listed in the Open Food Facts additive taxonomy.";
    }

    private String actionForAdditive(TaxonomyEntry additive) {
        List<String> classActions = nullToEmpty(additive.classes()).stream()
                .map(this::actionForClass)
                .filter(action -> action != null && !action.isBlank())
                .distinct()
                .toList();
        if (!classActions.isEmpty()) {
            return String.join(" ", classActions);
        }
        return "NutriTrust reports this additive as a factual presence flag from product additive tags, additive code mentions, or ingredient text.";
    }

    private String descriptionForAllergen(TaxonomyEntry allergen) {
        String description = cleanText(allergen.description());
        if (description != null) {
            return description;
        }
        return displayName(allergen) + " is an allergen taxonomy entry from Open Food Facts.";
    }

    private String actionForAllergen(TaxonomyEntry allergen) {
        return "NutriTrust reports this when product allergen fields, trace fields, ingredient text, or ingredient taxonomy links indicate " + displayName(allergen) + ". This is factual label evidence, not a medical assessment.";
    }

    private String descriptionForIngredient(TaxonomyEntry ingredient) {
        String description = cleanText(ingredient.description());
        if (description != null) {
            return description;
        }
        return displayName(ingredient) + " is an ingredient or ingredient family entry from the Open Food Facts ingredient taxonomy.";
    }

    private String descriptionForClass(String classId) {
        TaxonomyEntry classEntry = additiveClassEntries.get(canonicalTag(classId));
        String description = classEntry == null ? null : cleanText(classEntry.description());
        if (description != null) {
            return description;
        }
        return displayForId(classId) + " is an additive function class in the Open Food Facts taxonomy.";
    }

    private String actionForClass(String classId) {
        String normalized = canonicalTag(classId);
        return switch (normalized) {
            case "en:acid", "en:acidifier" -> "It increases acidity or gives food a sour taste.";
            case "en:acidity-regulator" -> "It helps adjust or control acidity and pH in the food.";
            case "en:anti-caking-agent" -> "It helps powders or granules flow by reducing clumping.";
            case "en:anti-foaming-agent" -> "It helps reduce or prevent foam during processing or preparation.";
            case "en:antioxidant", "en:natural-antioxidant" -> "It helps slow oxidation-related changes such as rancidity, browning, or flavor loss.";
            case "en:bleaching-agent" -> "It changes or lightens color in an ingredient or food matrix.";
            case "en:bread-improver" -> "It helps modify dough handling, texture, volume, or baking performance.";
            case "en:bulking-agent" -> "It adds volume or body without necessarily adding strong flavor.";
            case "en:carbonating-agent" -> "It helps generate or maintain carbonation.";
            case "en:carrier" -> "It helps dissolve, dilute, disperse, or carry another additive or ingredient.";
            case "en:coagulant" -> "It helps proteins or particles set, curdle, or form a firmer structure.";
            case "en:colour", "en:natural-colours", "en:concentrated-plant-colour", "en:crust-colorant" -> "It adds, restores, or standardizes color in the food.";
            case "en:colour-retention-agent", "en:colour-stabiliser" -> "It helps preserve or stabilize color during storage or processing.";
            case "en:emulsifier", "en:natural-emulsifier" -> "It helps ingredients that normally separate, such as oil and water, stay mixed.";
            case "en:emulsifying-salts" -> "It helps proteins and fats form a smooth, stable emulsion, especially in processed cheese-style foods.";
            case "en:enhancer", "en:flavour-enhancer" -> "It strengthens or modifies perceived flavor without necessarily adding a distinct flavor of its own.";
            case "en:firming-agent", "en:natural-firming-agent" -> "It helps maintain firmness or crispness in the food structure.";
            case "en:flour-treatment-agent" -> "It modifies flour or dough behavior during processing or baking.";
            case "en:foaming-agent" -> "It helps create or maintain foam or aerated texture.";
            case "en:fruit-preservative", "en:preservative", "en:natural-preservative" -> "It helps slow spoilage or quality loss during storage.";
            case "en:gelling-agent" -> "It helps form a gel-like structure.";
            case "en:glazing-agent" -> "It helps create a shiny coating or protective surface layer.";
            case "en:humectant" -> "It helps retain moisture and manage texture.";
            case "en:modified-starch" -> "It helps modify thickness, texture, stability, or moisture behavior.";
            case "en:packaging-gas", "en:propellent-gas" -> "It is used as a gas for packaging, dispensing, or protecting the product environment.";
            case "en:raising-agent" -> "It helps doughs and batters release gas and rise.";
            case "en:release-agent" -> "It helps prevent sticking during processing or packaging.";
            case "en:salt-substitute" -> "It provides salty taste or mineral functionality while replacing some sodium chloride.";
            case "en:sequestrant" -> "It binds metal ions, which can help stabilize color, flavor, or shelf quality.";
            case "en:stabiliser", "en:natural-stabilizer" -> "It helps maintain texture, structure, mixture stability, or dispersion.";
            case "en:sweetener" -> "It adds sweetness to the food.";
            case "en:thickener", "en:natural-thickener" -> "It increases viscosity or gives the food a thicker texture.";
            default -> {
                TaxonomyEntry classEntry = additiveClassEntries.get(normalized);
                String description = classEntry == null ? null : cleanText(classEntry.description());
                yield description == null ? "NutriTrust reports this class as a factual Open Food Facts function category." : description;
            }
        };
    }

    private List<String> sourceUrls(TaxonomyEntry entry) {
        if (entry == null) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        String wikipediaUrl = cleanText(entry.wikipediaUrl());
        if (wikipediaUrl != null) {
            urls.add(wikipediaUrl);
        }
        String wikidataId = cleanText(entry.wikidataId());
        if (wikidataId != null) {
            urls.add("https://www.wikidata.org/wiki/" + wikidataId);
        }
        return deduplicate(urls);
    }

    private String formattedNutritionValue(Double value, String unit) {
        String normalizedUnit = textOrDefault(unit, "g per 100g");
        String separator = normalizedUnit.startsWith("g ") ? "" : " ";
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString() + separator + normalizedUnit;
    }

    private String textOrDefault(String value, String defaultValue) {
        String cleaned = cleanText(value);
        return cleaned == null ? defaultValue : cleaned;
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

    private record NutritionValue(Double value, boolean calculatedFromFallback) {
    }

    private static class IngredientAccumulator {
        private final String category;
        private final String taxonomyId;
        private final String displayName;
        private final List<String> classes;
        private final String explanation;
        private final String description;
        private final String functionDescription;
        private final List<String> sourceUrls;
        private final List<String> terms = new ArrayList<>();
        private final List<String> taxonomyIds = new ArrayList<>();

        private IngredientAccumulator(
                String category,
                String taxonomyId,
                String displayName,
                List<String> classes,
                String explanation,
                String description,
                String functionDescription,
                List<String> sourceUrls
        ) {
            this.category = category;
            this.taxonomyId = taxonomyId;
            this.displayName = displayName;
            this.classes = classes;
            this.explanation = explanation;
            this.description = description;
            this.functionDescription = functionDescription;
            this.sourceUrls = sourceUrls;
        }

        private void addTerm(String term) {
            if (term != null) {
                terms.add(term);
            }
        }

        private void addTerms(List<String> terms) {
            if (terms != null) {
                this.terms.addAll(terms);
            }
        }

        private void addTaxonomyId(String taxonomyId) {
            if (taxonomyId != null) {
                taxonomyIds.add(taxonomyId);
            }
        }

        private void addTaxonomyIds(List<String> taxonomyIds) {
            if (taxonomyIds != null) {
                this.taxonomyIds.addAll(taxonomyIds);
            }
        }

        private IngredientFlag toFlag() {
            return new IngredientFlag(category, cap(terms), explanation, taxonomyId, displayName, classes, dedupe(taxonomyIds), description, functionDescription, sourceUrls);
        }
    }

    private static class AdditiveAccumulator {
        private final String name;
        private String source;
        private final String explanation;
        private final String taxonomyId;
        private final String displayName;
        private final List<String> classes;
        private final String description;
        private final String functionDescription;
        private final List<String> sourceUrls;
        private final List<String> taxonomyIds = new ArrayList<>();
        private final List<String> matchedTerms = new ArrayList<>();

        private AdditiveAccumulator(
                String name,
                String source,
                String explanation,
                String taxonomyId,
                String displayName,
                List<String> classes,
                String description,
                String functionDescription,
                List<String> sourceUrls
        ) {
            this.name = name;
            this.source = source;
            this.explanation = explanation;
            this.taxonomyId = taxonomyId;
            this.displayName = displayName;
            this.classes = classes;
            this.description = description;
            this.functionDescription = functionDescription;
            this.sourceUrls = sourceUrls;
        }

        private void addSource(String source) {
            if ("ingredients_text".equals(this.source)) {
                return;
            }
            this.source = source;
        }

        private void addTaxonomyId(String taxonomyId) {
            if (taxonomyId != null) {
                taxonomyIds.add(taxonomyId);
            }
        }

        private void addMatchedTerm(String term) {
            if (term != null) {
                matchedTerms.add(term);
            }
        }

        private AdditiveFlag toFlag() {
            return new AdditiveFlag(name, source, explanation, taxonomyId, displayName, classes, dedupe(taxonomyIds), cap(matchedTerms), description, functionDescription, sourceUrls);
        }
    }

    private static class AllergenAccumulator {
        private final String name;
        private String source;
        private final String explanation;
        private final String taxonomyId;
        private final String displayName;
        private final List<String> classes;
        private final String description;
        private final String functionDescription;
        private final List<String> sourceUrls;
        private final List<String> taxonomyIds = new ArrayList<>();
        private final List<String> matchedTerms = new ArrayList<>();

        private AllergenAccumulator(
                String name,
                String source,
                String explanation,
                String taxonomyId,
                String displayName,
                List<String> classes,
                String description,
                String functionDescription,
                List<String> sourceUrls
        ) {
            this.name = name;
            this.source = source;
            this.explanation = explanation;
            this.taxonomyId = taxonomyId;
            this.displayName = displayName;
            this.classes = classes;
            this.description = description;
            this.functionDescription = functionDescription;
            this.sourceUrls = sourceUrls;
        }

        private void addSource(String source) {
            if ("allergen_fields".equals(this.source)) {
                return;
            }
            this.source = source;
        }

        private void addTaxonomyId(String taxonomyId) {
            if (taxonomyId != null) {
                taxonomyIds.add(taxonomyId);
            }
        }

        private void addMatchedTerm(String term) {
            if (term != null) {
                matchedTerms.add(term);
            }
        }

        private AllergenFlag toFlag() {
            return new AllergenFlag(name, source, explanation, taxonomyId, displayName, classes, dedupe(taxonomyIds), cap(matchedTerms), description, functionDescription, sourceUrls);
        }
    }

    private static List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static List<String> cap(List<String> values) {
        List<String> deduped = dedupe(values);
        return deduped.size() <= MAX_MATCHED_TERMS ? deduped : deduped.subList(0, MAX_MATCHED_TERMS);
    }
}
