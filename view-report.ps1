param(
    [string]$Barcode = "8901088068758",
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$Json
)

$ErrorActionPreference = "Stop"
$url = "$BaseUrl/api/products/report/$Barcode"

try {
    $raw = curl.exe -s $url
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "Empty response from $url"
    }
    $report = $raw | ConvertFrom-Json
} catch {
    Write-Host "Could not fetch or parse product report." -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}

if ($Json) {
    $report | ConvertTo-Json -Depth 20
    exit 0
}

if ($report.status -and $report.error) {
    Write-Host "API Error" -ForegroundColor Red
    Write-Host "Status : $($report.status)"
    Write-Host "Error  : $($report.error)"
    Write-Host "Message: $($report.message)"
    exit 1
}

Write-Host ""
Write-Host "NutriTrust Product Report" -ForegroundColor Cyan
Write-Host "========================="
Write-Host "Barcode : $($report.barcode)"
Write-Host "Product : $($report.productName)"
Write-Host "Brand   : $($report.brand)"
Write-Host "Category: $($report.category)"
if ($report.ingredientText) {
    Write-Host "Ingredients: $($report.ingredientText)"
}

if ($report.found -eq $false) {
    Write-Host ""
    Write-Host "Product not found." -ForegroundColor Yellow
    exit 0
}

function Write-Section {
    param(
        [string]$Title
    )
    Write-Host ""
    Write-Host $Title -ForegroundColor Yellow
    Write-Host ("-" * $Title.Length)
}

function Has-DataQualityWarning {
    param(
        [string]$Field
    )
    if (-not $report.dataQualityWarnings) {
        return $false
    }
    foreach ($warning in $report.dataQualityWarnings) {
        if ($warning.field -eq $Field) {
            return $true
        }
    }
    return $false
}

Write-Section "Nutrition Flags"
if ($report.nutritionFlags.Count -eq 0) {
    Write-Host "None"
} else {
    foreach ($flag in $report.nutritionFlags) {
        Write-Host "$($flag.name): $($flag.level) ($($flag.value))"
        Write-Host "  $($flag.explanation)"
    }
}

Write-Section "Ingredient Flags"
if ($report.ingredientFlags.Count -eq 0) {
    if (Has-DataQualityWarning "ingredients") {
        Write-Host "Not evaluated - ingredient data was unavailable or incomplete from the source."
    } else {
        Write-Host "None"
    }
} else {
    foreach ($flag in $report.ingredientFlags) {
        $terms = if ($flag.matchedTerms) { $flag.matchedTerms -join ", " } else { "None" }
        Write-Host "$($flag.category)"
        Write-Host "  Matched: $terms"
        Write-Host "  $($flag.explanation)"
    }
}

Write-Section "Additive Flags"
if ($report.additiveFlags.Count -eq 0) {
    if (Has-DataQualityWarning "ingredients") {
        Write-Host "Not evaluated - additive detection depends on ingredient/additive data, which may be incomplete."
    } else {
        Write-Host "None"
    }
} else {
    foreach ($flag in $report.additiveFlags) {
        Write-Host "$($flag.name) [$($flag.source)]"
        Write-Host "  $($flag.explanation)"
    }
}

Write-Section "Allergen Flags"
if ($report.allergenFlags.Count -eq 0) {
    if (Has-DataQualityWarning "allergens") {
        Write-Host "Not evaluated - allergen/traces data was unavailable or incomplete from the source."
    } else {
        Write-Host "None"
    }
} else {
    foreach ($flag in $report.allergenFlags) {
        Write-Host "$($flag.name) [$($flag.source)]"
        Write-Host "  $($flag.explanation)"
    }
}

Write-Section "Positive Signals"
if ($report.positiveSignals.Count -eq 0) {
    Write-Host "None"
} else {
    foreach ($signal in $report.positiveSignals) {
        Write-Host "$($signal.name): $($signal.level) ($($signal.value))"
        Write-Host "  $($signal.explanation)"
    }
}

Write-Section "Data Quality Warnings"
if ($report.dataQualityWarnings.Count -eq 0) {
    Write-Host "None"
} else {
    foreach ($warning in $report.dataQualityWarnings) {
        Write-Host "$($warning.field)"
        Write-Host "  $($warning.message)"
    }
}

Write-Section "AI Report"
Write-Host $report.aiReport
Write-Host ""
