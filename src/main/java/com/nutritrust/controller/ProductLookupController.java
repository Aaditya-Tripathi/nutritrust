package com.nutritrust.controller;

import com.nutritrust.dto.ProductLookupResponse;
import com.nutritrust.service.ProductLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*"
})
public class ProductLookupController {

    private final ProductLookupService productLookupService;

    public ProductLookupController(ProductLookupService productLookupService) {
        this.productLookupService = productLookupService;
    }

    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductLookupResponse> getProductByBarcode(@PathVariable String barcode) {
        ProductLookupResponse response = productLookupService.lookupByBarcode(barcode);
        if (!response.found()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
