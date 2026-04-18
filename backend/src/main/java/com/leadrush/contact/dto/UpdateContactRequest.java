package com.leadrush.contact.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateContactRequest {

    private String firstName;
    private String lastName;
    private String title;
    private String lifecycleStage;
    private String avatarUrl;
    private String website;
    private String linkedinUrl;
    private String twitterUrl;
}
