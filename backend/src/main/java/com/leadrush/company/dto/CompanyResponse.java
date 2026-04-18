package com.leadrush.company.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyResponse {

    private UUID id;
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
    private long contactCount;          // how many contacts belong to this company
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
