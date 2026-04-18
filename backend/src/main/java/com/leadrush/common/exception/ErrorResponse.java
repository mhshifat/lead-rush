package com.leadrush.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Error response envelope returned for all failures. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private boolean success;
    private ErrorBody error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorBody {
        private ErrorCategory category;
        private String message;
        private String correlationId;           // only for SYSTEM errors
        private List<FieldError> details;       // only for VALIDATION errors
        private String lastActivationEmailSentAt; // only for ACTIVATION_REQUIRED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }

    public static ErrorResponse validation(String message, List<FieldError> details) {
        return ErrorResponse.builder()
                .success(false)
                .error(ErrorBody.builder()
                        .category(ErrorCategory.VALIDATION)
                        .message(message)
                        .details(details)
                        .build())
                .build();
    }

    public static ErrorResponse business(String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(ErrorBody.builder()
                        .category(ErrorCategory.BUSINESS)
                        .message(message)
                        .build())
                .build();
    }

    public static ErrorResponse auth(String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(ErrorBody.builder()
                        .category(ErrorCategory.AUTH)
                        .message(message)
                        .build())
                .build();
    }

    public static ErrorResponse system(String correlationId, String message) {
        return ErrorResponse.builder()
                .success(false)
                .error(ErrorBody.builder()
                        .category(ErrorCategory.SYSTEM)
                        .message(message)
                        .correlationId(correlationId)
                        .build())
                .build();
    }

    public static ErrorResponse activationRequired(String message, String lastSentAt) {
        return ErrorResponse.builder()
                .success(false)
                .error(ErrorBody.builder()
                        .category(ErrorCategory.ACTIVATION_REQUIRED)
                        .message(message)
                        .lastActivationEmailSentAt(lastSentAt)
                        .build())
                .build();
    }
}
