package com.nutritrust.repository;

import com.nutritrust.entity.ProductReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductReportRepository extends JpaRepository<ProductReportEntity, Long> {

    List<ProductReportEntity> findAllByOrderByCreatedAtDesc();

    Optional<ProductReportEntity> findFirstByBarcodeOrderByCreatedAtDesc(String barcode);
}
