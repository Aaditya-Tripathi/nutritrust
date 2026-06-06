package com.nutritrust.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritrust.dto.AdditiveFlag;
import com.nutritrust.dto.AllergenFlag;
import com.nutritrust.dto.DataQualityWarning;
import com.nutritrust.dto.IngredientFlag;
import com.nutritrust.dto.NutritionFlag;
import com.nutritrust.dto.PositiveSignal;
import com.nutritrust.dto.ReportHistoryItemResponse;
import com.nutritrust.dto.SavedProductReportResponse;
import com.nutritrust.entity.ProductReportEntity;
import com.nutritrust.exception.ReportNotFoundException;
import com.nutritrust.repository.ProductReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportHistoryService {

    private final ProductReportRepository productReportRepository;
    private final ObjectMapper objectMapper;

    public ReportHistoryService(ProductReportRepository productReportRepository, ObjectMapper objectMapper) {
        this.productReportRepository = productReportRepository;
        this.objectMapper = objectMapper;
    }

    public List<ReportHistoryItemResponse> getReportHistory() {
        return productReportRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toHistoryItem)
                .toList();
    }

    public SavedProductReportResponse getSavedReport(Long id) {
        ProductReportEntity entity = productReportRepository.findById(id)
                .orElseThrow(() -> new ReportNotFoundException("Saved report not found for id: " + id));
        return toSavedReport(entity);
    }

    public void deleteSavedReport(Long id) {
        if (!productReportRepository.existsById(id)) {
            throw new ReportNotFoundException("Saved report not found for id: " + id);
        }
        productReportRepository.deleteById(id);
    }

    private ReportHistoryItemResponse toHistoryItem(ProductReportEntity entity) {
        return new ReportHistoryItemResponse(
                entity.getId(),
                entity.getBarcode(),
                entity.getProductName(),
                entity.getBrand(),
                entity.getCategory(),
                entity.getCreatedAt()
        );
    }

    private SavedProductReportResponse toSavedReport(ProductReportEntity entity) {
        return new SavedProductReportResponse(
                entity.getId(),
                entity.getBarcode(),
                entity.getProductName(),
                entity.getBrand(),
                entity.getCategory(),
                entity.getIngredients(),
                readList(entity.getNutritionFlagsJson(), new TypeReference<List<NutritionFlag>>() {}),
                readList(entity.getIngredientFlagsJson(), new TypeReference<List<IngredientFlag>>() {}),
                readList(entity.getAdditiveFlagsJson(), new TypeReference<List<AdditiveFlag>>() {}),
                readList(entity.getAllergenFlagsJson(), new TypeReference<List<AllergenFlag>>() {}),
                readList(entity.getPositiveSignalsJson(), new TypeReference<List<PositiveSignal>>() {}),
                readList(entity.getDataQualityWarningsJson(), new TypeReference<List<DataQualityWarning>>() {}),
                entity.getAiReport(),
                entity.getCreatedAt()
        );
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
