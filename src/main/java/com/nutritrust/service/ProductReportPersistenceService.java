package com.nutritrust.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritrust.dto.ProductReportResponse;
import com.nutritrust.entity.ProductReportEntity;
import com.nutritrust.repository.ProductReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ProductReportPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductReportPersistenceService.class);
    private static final String AI_UNAVAILABLE_MESSAGE = "AI explanation unavailable. Please review the factual flags.";

    private final ProductReportRepository productReportRepository;
    private final ObjectMapper objectMapper;

    public ProductReportPersistenceService(ProductReportRepository productReportRepository, ObjectMapper objectMapper) {
        this.productReportRepository = productReportRepository;
        this.objectMapper = objectMapper;
    }

    public void saveReport(ProductReportResponse report) {
        if (report == null || !report.found()) {
            return;
        }

        try {
            ProductReportEntity entity = toEntity(report);
            productReportRepository.findFirstByBarcodeOrderByCreatedAtDesc(report.barcode())
                    .filter(existing -> hasSameDeterministicReport(existing, entity))
                    .ifPresentOrElse(
                            existing -> updateAiReportIfNeeded(existing, entity),
                            () -> productReportRepository.save(entity)
                    );
        } catch (RuntimeException | JsonProcessingException ex) {
            LOGGER.warn("Failed to save product report for barcode {}", report.barcode(), ex);
        }
    }

    private ProductReportEntity toEntity(ProductReportResponse report) throws JsonProcessingException {
        ProductReportEntity entity = new ProductReportEntity();
        entity.setBarcode(report.barcode());
        entity.setProductName(report.productName());
        entity.setBrand(report.brand());
        entity.setCategory(report.category());
        entity.setIngredients(report.ingredientText());
        entity.setNutritionFlagsJson(toJson(report.nutritionFlags()));
        entity.setIngredientFlagsJson(toJson(report.ingredientFlags()));
        entity.setAdditiveFlagsJson(toJson(report.additiveFlags()));
        entity.setAllergenFlagsJson(toJson(report.allergenFlags()));
        entity.setPositiveSignalsJson(toJson(report.positiveSignals()));
        entity.setDataQualityWarningsJson(toJson(report.dataQualityWarnings()));
        entity.setAiReport(report.aiReport());
        return entity;
    }

    private boolean hasSameDeterministicReport(ProductReportEntity existing, ProductReportEntity current) {
        return Objects.equals(existing.getBarcode(), current.getBarcode())
                && Objects.equals(existing.getProductName(), current.getProductName())
                && Objects.equals(existing.getBrand(), current.getBrand())
                && Objects.equals(existing.getCategory(), current.getCategory())
                && Objects.equals(existing.getIngredients(), current.getIngredients())
                && Objects.equals(existing.getNutritionFlagsJson(), current.getNutritionFlagsJson())
                && Objects.equals(existing.getIngredientFlagsJson(), current.getIngredientFlagsJson())
                && Objects.equals(existing.getAdditiveFlagsJson(), current.getAdditiveFlagsJson())
                && Objects.equals(existing.getAllergenFlagsJson(), current.getAllergenFlagsJson())
                && Objects.equals(existing.getPositiveSignalsJson(), current.getPositiveSignalsJson())
                && Objects.equals(existing.getDataQualityWarningsJson(), current.getDataQualityWarningsJson());
    }

    private void updateAiReportIfNeeded(ProductReportEntity existing, ProductReportEntity current) {
        if (Objects.equals(existing.getAiReport(), current.getAiReport()) || isAiUnavailable(current.getAiReport())) {
            return;
        }

        existing.setAiReport(current.getAiReport());
        productReportRepository.save(existing);
    }

    private boolean isAiUnavailable(String aiReport) {
        return aiReport == null || aiReport.isBlank() || AI_UNAVAILABLE_MESSAGE.equals(aiReport);
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }
}
