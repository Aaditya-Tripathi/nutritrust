package com.nutritrust.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritrust.dto.ProductReportResponse;
import com.nutritrust.entity.ProductReportEntity;
import com.nutritrust.repository.ProductReportRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductReportPersistenceServiceTest {

    private final ProductReportRepository repository = mock(ProductReportRepository.class);
    private final ProductReportPersistenceService service = new ProductReportPersistenceService(repository, new ObjectMapper());

    @Test
    void savesReportWhenNoPreviousBarcodeReportExists() {
        ProductReportResponse report = report();
        when(repository.findFirstByBarcodeOrderByCreatedAtDesc("1234567890123")).thenReturn(Optional.empty());

        service.saveReport(report);

        verify(repository).save(any(ProductReportEntity.class));
    }

    @Test
    void skipsSaveWhenLatestBarcodeReportHasSameDeterministicContent() {
        ProductReportResponse report = report();
        when(repository.findFirstByBarcodeOrderByCreatedAtDesc("1234567890123")).thenReturn(Optional.of(entityMatching(report)));

        service.saveReport(report);

        verify(repository, never()).save(any(ProductReportEntity.class));
    }

    private ProductReportResponse report() {
        return new ProductReportResponse(
                true,
                "1234567890123",
                "Test Product",
                "Test Brand",
                "Test Category",
                "Test ingredients",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "AI explanation unavailable. Please review the factual flags."
        );
    }

    private ProductReportEntity entityMatching(ProductReportResponse report) {
        ProductReportEntity entity = new ProductReportEntity();
        entity.setBarcode(report.barcode());
        entity.setProductName(report.productName());
        entity.setBrand(report.brand());
        entity.setCategory(report.category());
        entity.setIngredients(report.ingredientText());
        entity.setNutritionFlagsJson("[]");
        entity.setIngredientFlagsJson("[]");
        entity.setAdditiveFlagsJson("[]");
        entity.setAllergenFlagsJson("[]");
        entity.setPositiveSignalsJson("[]");
        entity.setDataQualityWarningsJson("[]");
        entity.setAiReport(report.aiReport());
        return entity;
    }
}
