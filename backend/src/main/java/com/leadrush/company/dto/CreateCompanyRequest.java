package com.leadrush.company.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCompanyRequest {

    @NotBlank(message = "Company name is required")
    private String name;

    private String domain;
    private String industry;
    private String companySize;
    private String annualRevenue;
    private String description;
    private String website;
    private String logoUrl;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String country;
    private String zipCode;
}
