package com.leadrush.contact.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateContactRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    private String lastName;
    private String title;
    private String companyName;         // auto-find or create company by name
    private String lifecycleStage;
    private String source;

    private String avatarUrl;
    private String website;
    private String linkedinUrl;
    private String twitterUrl;

    private List<EmailDto> emails;
    private List<PhoneDto> phones;
    private List<String> tags;          // tag names — auto-create if not exist

    @Data
    public static class EmailDto {
        private String email;
        private String emailType;       // WORK, PERSONAL, OTHER
        private boolean primary;
    }

    @Data
    public static class PhoneDto {
        private String phone;
        private String phoneType;       // WORK, MOBILE, PERSONAL, OTHER
        private boolean primary;
    }
}
