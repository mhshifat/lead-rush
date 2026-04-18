package com.leadrush.email.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.email.dto.CreateMailboxRequest;
import com.leadrush.email.dto.MailboxResponse;
import com.leadrush.email.service.MailboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mailboxes")
@RequiredArgsConstructor
public class MailboxController {

    private final MailboxService mailboxService;

    @GetMapping
    public ApiResponse<List<MailboxResponse>> listMailboxes() {
        return ApiResponse.success(mailboxService.listMailboxes());
    }

    @GetMapping("/{id}")
    public ApiResponse<MailboxResponse> getMailbox(@PathVariable UUID id) {
        return ApiResponse.success(mailboxService.getMailbox(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MailboxResponse>> connectMailbox(
            @Valid @RequestBody CreateMailboxRequest request
    ) {
        MailboxResponse mailbox = mailboxService.connectMailbox(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(mailbox));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Boolean>> testMailbox(@PathVariable UUID id) {
        boolean connected = mailboxService.testMailbox(id);
        return ApiResponse.success(Map.of("connected", connected));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMailbox(@PathVariable UUID id) {
        mailboxService.deleteMailbox(id);
        return ApiResponse.success("Mailbox deleted");
    }
}
