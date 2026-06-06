package com.nutritrust.controller;

import com.nutritrust.dto.ReportHistoryItemResponse;
import com.nutritrust.dto.SavedProductReportResponse;
import com.nutritrust.service.ReportHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*"
})
public class ReportHistoryController {

    private final ReportHistoryService reportHistoryService;

    public ReportHistoryController(ReportHistoryService reportHistoryService) {
        this.reportHistoryService = reportHistoryService;
    }

    @GetMapping
    public ResponseEntity<List<ReportHistoryItemResponse>> getReportHistory() {
        return ResponseEntity.ok(reportHistoryService.getReportHistory());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SavedProductReportResponse> getSavedReport(@PathVariable Long id) {
        return ResponseEntity.ok(reportHistoryService.getSavedReport(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSavedReport(@PathVariable Long id) {
        reportHistoryService.deleteSavedReport(id);
        return ResponseEntity.noContent().build();
    }
}
