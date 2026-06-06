package com.nutritrust.flags;

import com.nutritrust.dto.AdditiveFlag;
import com.nutritrust.dto.AllergenFlag;
import com.nutritrust.dto.IngredientFlag;
import com.nutritrust.dto.NutritionFlag;
import com.nutritrust.dto.PositiveSignal;

import java.util.List;

public record FlagEvaluation(
        List<NutritionFlag> nutritionFlags,
        List<IngredientFlag> ingredientFlags,
        List<AdditiveFlag> additiveFlags,
        List<AllergenFlag> allergenFlags,
        List<PositiveSignal> positiveSignals
) {
}
