package com.leadrush.company.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {})
public class Company extends TenantEntity {

    @Column(nullable = false)
    private String name;

    private String domain;
    private String industry;
    private String companySize;
    private String annualRevenue;
    private String website;

    @Column(columnDefinition = "text")
    private String description;

    private String logoUrl;
    private String phone;

    @Column(columnDefinition = "text")
    private String address;

    private String city;
    private String state;
    private String country;
    private String zipCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";
}
