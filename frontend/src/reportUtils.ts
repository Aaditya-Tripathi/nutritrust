import type {
  DataQualityWarning,
  NormalizedReport,
  ProductReportResponse,
  SavedProductReportResponse,
} from './types'

export function normalizeLiveReport(report: ProductReportResponse): NormalizedReport {
  return {
    found: report.found,
    barcode: report.barcode,
    productName: report.productName,
    brand: report.brand,
    category: report.category,
    ingredientText: report.ingredientText,
    nutritionFlags: report.nutritionFlags ?? [],
    ingredientFlags: report.ingredientFlags ?? [],
    additiveFlags: report.additiveFlags ?? [],
    allergenFlags: report.allergenFlags ?? [],
    positiveSignals: report.positiveSignals ?? [],
    dataQualityWarnings: report.dataQualityWarnings ?? [],
    aiReport: report.aiReport,
  }
}

export function normalizeSavedReport(report: SavedProductReportResponse): NormalizedReport {
  return {
    found: true,
    id: report.id,
    barcode: report.barcode,
    productName: report.productName,
    brand: report.brand,
    category: report.category,
    ingredientText: report.ingredients,
    nutritionFlags: report.nutritionFlags ?? [],
    ingredientFlags: report.ingredientFlags ?? [],
    additiveFlags: report.additiveFlags ?? [],
    allergenFlags: report.allergenFlags ?? [],
    positiveSignals: report.positiveSignals ?? [],
    dataQualityWarnings: report.dataQualityWarnings ?? [],
    aiReport: report.aiReport,
    createdAt: report.createdAt,
  }
}

export function hasWarning(warnings: DataQualityWarning[], field: string) {
  return warnings.some((warning) => warning.field.toLowerCase() === field.toLowerCase())
}

export function hasAnyWarning(warnings: DataQualityWarning[], fields: string[]) {
  return fields.some((field) => hasWarning(warnings, field))
}

export function formatDate(value?: string) {
  if (!value) {
    return 'Not available'
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function labelForMissing(value?: string | null) {
  return value && value.trim() ? value : 'Not available'
}

export function toneForLevel(level: string) {
  const normalized = level.toLowerCase()
  if (normalized === 'high') {
    return 'danger'
  }
  if (normalized === 'medium' || normalized === 'moderate') {
    return 'warning'
  }
  if (normalized === 'good' || normalized === 'low') {
    return 'success'
  }
  return 'neutral'
}
