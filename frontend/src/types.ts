export type NutritionFlag = {
  name: string
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'MODERATE' | 'GOOD' | string
  value: string
  explanation: string
  description?: string
  functionDescription?: string
  sourceUrls?: string[]
}

export type IngredientFlag = {
  category: string
  matchedTerms: string[]
  explanation: string
  taxonomyId?: string
  displayName?: string
  classes?: string[]
  matchedTaxonomyIds?: string[]
  description?: string
  functionDescription?: string
  sourceUrls?: string[]
}

export type AdditiveFlag = {
  name: string
  source: string
  explanation: string
  taxonomyId?: string
  displayName?: string
  classes?: string[]
  matchedTaxonomyIds?: string[]
  matchedTerms?: string[]
  description?: string
  functionDescription?: string
  sourceUrls?: string[]
}

export type AllergenFlag = {
  name: string
  source: string
  explanation: string
  taxonomyId?: string
  displayName?: string
  classes?: string[]
  matchedTaxonomyIds?: string[]
  matchedTerms?: string[]
  description?: string
  functionDescription?: string
  sourceUrls?: string[]
}

export type PositiveSignal = {
  name: string
  level: string
  value: string
  explanation: string
}

export type DataQualityWarning = {
  field: string
  message: string
}

export type ProductReportRequest = {
  barcode: string
  manualIngredientsText?: string
  manualAllergenText?: string
  manualNutritionNote?: string
  groqApiKey?: string
}

export type GroqApiKeyTestResponse = {
  ok: boolean
  message: string
}

export type ProductReportResponse = {
  found: boolean
  barcode: string
  productName?: string | null
  brand?: string | null
  category?: string | null
  ingredientText?: string | null
  nutritionFlags: NutritionFlag[]
  ingredientFlags: IngredientFlag[]
  additiveFlags: AdditiveFlag[]
  allergenFlags: AllergenFlag[]
  positiveSignals: PositiveSignal[]
  dataQualityWarnings: DataQualityWarning[]
  aiReport: string
}

export type ReportHistoryItem = {
  id: number
  barcode: string
  productName?: string | null
  brand?: string | null
  category?: string | null
  createdAt: string
}

export type DashboardSummary = {
  totalReports: number
  highSugarReports: number
  highSaltReports: number
  saturatedFatReports: number
  allergenWarningReports: number
  missingDataReports: number
  ingredientAdditiveReports: number
}

export type DashboardFlagDistributionItem = {
  category: string
  count: number
}

export type DashboardRecentReport = {
  id: number
  barcode: string
  productName?: string | null
  brand?: string | null
  category?: string | null
  createdAt?: string | null
  nutritionFlagCount: number
  ingredientFlagCount: number
  additiveFlagCount: number
  allergenFlagCount: number
  dataQualityWarningCount: number
  positiveSignalCount: number
  tags: string[]
}

export type DashboardResponse = {
  summary: DashboardSummary
  flagDistribution: DashboardFlagDistributionItem[]
  recentReports: DashboardRecentReport[]
}

export type SavedProductReportResponse = {
  id: number
  barcode: string
  productName?: string | null
  brand?: string | null
  category?: string | null
  ingredients?: string | null
  nutritionFlags: NutritionFlag[]
  ingredientFlags: IngredientFlag[]
  additiveFlags: AdditiveFlag[]
  allergenFlags: AllergenFlag[]
  positiveSignals: PositiveSignal[]
  dataQualityWarnings: DataQualityWarning[]
  aiReport: string
  createdAt: string
}

export type ErrorResponse = {
  status?: number
  error?: string
  message?: string
  timestamp?: string
}

export type NormalizedReport = {
  found: boolean
  id?: number
  barcode: string
  productName?: string | null
  brand?: string | null
  category?: string | null
  ingredientText?: string | null
  nutritionFlags: NutritionFlag[]
  ingredientFlags: IngredientFlag[]
  additiveFlags: AdditiveFlag[]
  allergenFlags: AllergenFlag[]
  positiveSignals: PositiveSignal[]
  dataQualityWarnings: DataQualityWarning[]
  aiReport: string
  createdAt?: string
}
