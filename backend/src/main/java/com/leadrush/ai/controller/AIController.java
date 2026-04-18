package com.leadrush.ai.controller;

import com.leadrush.ai.adapter.LLMAdapter;
import com.leadrush.ai.dto.GenerateEmailRequest;
import com.leadrush.ai.dto.GenerateEmailResponse;
import com.leadrush.ai.dto.SubjectSuggestionsRequest;
import com.leadrush.ai.service.AIEmailWriterService;
import com.leadrush.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIEmailWriterService emailWriterService;
    private final LLMAdapter llmAdapter;

    /** Frontend probes this to decide whether to show the AI buttons. */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(Map.of(
                "ready", llmAdapter.isReady(),
                "provider", llmAdapter.providerKey()
        ));
    }

    @PostMapping("/generate-email")
    public ApiResponse<GenerateEmailResponse> generateEmail(
            @Valid @RequestBody GenerateEmailRequest request
    ) {
        return ApiResponse.success(emailWriterService.generateEmail(request));
    }

    @PostMapping("/suggest-subject-lines")
    public ApiResponse<Map<String, List<String>>> suggestSubjects(
            @Valid @RequestBody SubjectSuggestionsRequest request
    ) {
        return ApiResponse.success(Map.of("suggestions", emailWriterService.suggestSubjects(request)));
    }
}
