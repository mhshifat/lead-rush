package com.leadrush.contact.controller;

import com.leadrush.activity.dto.ActivityEvent;
import com.leadrush.activity.service.ActivityService;
import com.leadrush.common.ApiResponse;
import com.leadrush.contact.dto.*;
import com.leadrush.contact.service.ContactService;
import com.leadrush.pipeline.dto.DealResponse;
import com.leadrush.pipeline.service.DealService;
import com.leadrush.sequence.dto.EnrollmentResponse;
import com.leadrush.sequence.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final EnrollmentService enrollmentService;
    private final ActivityService activityService;
    private final DealService dealService;

    @GetMapping
    public ApiResponse<Page<ContactResponse>> listContacts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String lifecycleStage,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) Integer minScore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ContactResponse> contacts = contactService.listContacts(
                search, lifecycleStage, companyId, minScore, pageable
        );
        return ApiResponse.success(contacts);
    }

    @GetMapping("/{id}")
    public ApiResponse<ContactResponse> getContact(@PathVariable UUID id) {
        ContactResponse contact = contactService.getContact(id);
        return ApiResponse.success(contact);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ContactResponse>> createContact(
            @Valid @RequestBody CreateContactRequest request
    ) {
        ContactResponse contact = contactService.createContact(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(contact));
    }

    @PutMapping("/{id}")
    public ApiResponse<ContactResponse> updateContact(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContactRequest request
    ) {
        ContactResponse contact = contactService.updateContact(id, request);
        return ApiResponse.success(contact);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteContact(@PathVariable UUID id) {
        contactService.deleteContact(id);
        return ApiResponse.success("Contact deleted");
    }

    @GetMapping("/{id}/enrollments")
    public ApiResponse<List<EnrollmentResponse>> listContactEnrollments(@PathVariable UUID id) {
        contactService.getContact(id); // authz check
        return ApiResponse.success(enrollmentService.listEnrollmentsForContact(id));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<ActivityEvent>> getTimeline(@PathVariable UUID id) {
        contactService.getContact(id); // authz check
        return ApiResponse.success(activityService.getContactTimeline(id));
    }

    @GetMapping("/{id}/deals")
    public ApiResponse<List<DealResponse>> listContactDeals(@PathVariable UUID id) {
        contactService.getContact(id); // authz check
        return ApiResponse.success(dealService.listDealsByContact(id));
    }
}
