package com.nutritrust.controller;

import com.nutritrust.dto.GroqApiKeyTestRequest;
import com.nutritrust.dto.GroqApiKeyTestResponse;
import com.nutritrust.service.AiReportService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groq")
@CrossOrigin(originPatterns = {
        "http://localhost:*",
        "http://127.0.0.1:*"
})
public class GroqController {

    private final AiReportService aiReportService;

    public GroqController(AiReportService aiReportService) {
        this.aiReportService = aiReportService;
    }

    @PostMapping("/test-key")
    public GroqApiKeyTestResponse testApiKey(@RequestBody GroqApiKeyTestRequest request) {
        return aiReportService.testConnection(request == null ? null : request.groqApiKey());
    }
}
