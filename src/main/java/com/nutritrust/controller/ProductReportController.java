package com.nutritrust.controller;

import com.nutritrust.dto.ProductReportResponse;
import com.nutritrust.dto.ProductReportRequest;
import com.nutritrust.service.ProductReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*"
})
public class ProductReportController {

    private final ProductReportService productReportService;

    public ProductReportController(ProductReportService productReportService) {
        this.productReportService = productReportService;
    }

    @GetMapping("/report/{barcode}")
    public ResponseEntity<ProductReportResponse> getProductReport(@PathVariable String barcode) {
        ProductReportResponse response = productReportService.generateReport(barcode);
        if (!response.found()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/report")
    public ResponseEntity<ProductReportResponse> getProductReportWithManualLabelText(@RequestBody ProductReportRequest request) {
        ProductReportResponse response = productReportService.generateReport(request);
        if (!response.found()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
