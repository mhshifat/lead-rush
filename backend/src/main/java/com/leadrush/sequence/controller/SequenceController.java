package com.leadrush.sequence.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.sequence.dto.*;
import com.leadrush.sequence.service.EnrollmentService;
import com.leadrush.sequence.service.SequenceService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sequences")
@RequiredArgsConstructor
public class SequenceController {

    private final SequenceService sequenceService;
    private final EnrollmentService enrollmentService;

    // ── Sequence CRUD ──

    @GetMapping
    public ApiResponse<List<SequenceResponse>> listSequences() {
        return ApiResponse.success(sequenceService.listSequences());
    }

    @GetMapping("/{id}")
    public ApiResponse<SequenceResponse> getSequence(@PathVariable UUID id) {
        return ApiResponse.success(sequenceService.getSequence(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SequenceResponse>> createSequence(
            @Valid @RequestBody CreateSequenceRequest request
    ) {
        SequenceResponse sequence = sequenceService.createSequence(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(sequence));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<SequenceResponse> activateSequence(@PathVariable UUID id) {
        return ApiResponse.success(sequenceService.activateSequence(id));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<SequenceResponse> pauseSequence(@PathVariable UUID id) {
        return ApiResponse.success(sequenceService.pauseSequence(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteSequence(@PathVariable UUID id) {
        sequenceService.deleteSequence(id);
        return ApiResponse.success("Sequence deleted");
    }

    // ── Step management ──

    @PostMapping("/{id}/steps")
    public ApiResponse<SequenceResponse> addStep(
            @PathVariable UUID id,
            @Valid @RequestBody CreateStepRequest request
    ) {
        return ApiResponse.success(sequenceService.addStep(id, request));
    }

    @DeleteMapping("/{id}/steps/{stepId}")
    public ApiResponse<SequenceResponse> deleteStep(
            @PathVariable UUID id,
            @PathVariable UUID stepId
    ) {
        return ApiResponse.success(sequenceService.deleteStep(id, stepId));
    }

    // ── Enrollments ──

    @PostMapping("/{id}/enrollments")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enrollContact(
            @PathVariable UUID id,
            @Valid @RequestBody EnrollRequest request
    ) {
        EnrollmentResponse enrollment = enrollmentService.enrollContact(
                id, request.getContactId(), request.getMailboxId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(enrollment));
    }

    @GetMapping("/{id}/enrollments")
    public ApiResponse<List<EnrollmentResponse>> listEnrollments(@PathVariable UUID id) {
        return ApiResponse.success(enrollmentService.listEnrollmentsForSequence(id));
    }

    @Data
    public static class EnrollRequest {
        private UUID contactId;
        private UUID mailboxId;         // optional — defaults to sequence's defaultMailbox
    }
}
