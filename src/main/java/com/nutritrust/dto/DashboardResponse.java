package com.nutritrust.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        DashboardSummary summary,
        List<DashboardFlagDistributionItem> flagDistribution,
        List<DashboardRecentReport> recentReports
) {
    public record DashboardSummary(
            long totalReports,
            long highSugarReports,
            long highSaltReports,
            long saturatedFatReports,
            long allergenWarningReports,
            long missingDataReports,
            long ingredientAdditiveReports
    ) {
    }

    public record DashboardFlagDistributionItem(
            String category,
            long count
    ) {
    }

    public record DashboardRecentReport(
            Long id,
            String barcode,
            String productName,
            String brand,
            String category,
            LocalDateTime createdAt,
            int nutritionFlagCount,
            int ingredientFlagCount,
            int additiveFlagCount,
            int allergenFlagCount,
            int dataQualityWarningCount,
            int positiveSignalCount,
            List<String> tags
    ) {
    }
}
