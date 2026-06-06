package com.nutritrust.dto;

import java.time.LocalDateTime;

public record ReportHistoryItemResponse(
        Long id,
        String barcode,
        String productName,
        String brand,
        String category,
        LocalDateTime createdAt
) {
}
