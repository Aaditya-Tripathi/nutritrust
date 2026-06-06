package com.nutritrust.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_reports")
public class ProductReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String barcode;
    private String productName;
    private String brand;
    private String category;

    @Column(columnDefinition = "TEXT")
    private String ingredients;

    @Column(columnDefinition = "TEXT")
    private String nutritionFlagsJson;

    @Column(columnDefinition = "TEXT")
    private String ingredientFlagsJson;

    @Column(columnDefinition = "TEXT")
    private String additiveFlagsJson;

    @Column(columnDefinition = "TEXT")
    private String allergenFlagsJson;

    @Column(columnDefinition = "TEXT")
    private String positiveSignalsJson;

    @Column(columnDefinition = "TEXT")
    private String dataQualityWarningsJson;

    @Column(columnDefinition = "TEXT")
    private String aiReport;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIngredients() {
        return ingredients;
    }

    public void setIngredients(String ingredients) {
        this.ingredients = ingredients;
    }

    public String getNutritionFlagsJson() {
        return nutritionFlagsJson;
    }

    public void setNutritionFlagsJson(String nutritionFlagsJson) {
        this.nutritionFlagsJson = nutritionFlagsJson;
    }

    public String getIngredientFlagsJson() {
        return ingredientFlagsJson;
    }

    public void setIngredientFlagsJson(String ingredientFlagsJson) {
        this.ingredientFlagsJson = ingredientFlagsJson;
    }

    public String getAdditiveFlagsJson() {
        return additiveFlagsJson;
    }

    public void setAdditiveFlagsJson(String additiveFlagsJson) {
        this.additiveFlagsJson = additiveFlagsJson;
    }

    public String getAllergenFlagsJson() {
        return allergenFlagsJson;
    }

    public void setAllergenFlagsJson(String allergenFlagsJson) {
        this.allergenFlagsJson = allergenFlagsJson;
    }

    public String getPositiveSignalsJson() {
        return positiveSignalsJson;
    }

    public void setPositiveSignalsJson(String positiveSignalsJson) {
        this.positiveSignalsJson = positiveSignalsJson;
    }

    public String getDataQualityWarningsJson() {
        return dataQualityWarningsJson;
    }

    public void setDataQualityWarningsJson(String dataQualityWarningsJson) {
        this.dataQualityWarningsJson = dataQualityWarningsJson;
    }

    public String getAiReport() {
        return aiReport;
    }

    public void setAiReport(String aiReport) {
        this.aiReport = aiReport;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
