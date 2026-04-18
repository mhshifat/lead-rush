package com.leadrush.company.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.company.dto.*;
import com.leadrush.company.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ApiResponse<Page<CompanyResponse>> listCompanies(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(companyService.listCompanies(search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<CompanyResponse> getCompany(@PathVariable UUID id) {
        return ApiResponse.success(companyService.getCompany(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request
    ) {
        CompanyResponse company = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(company));
    }

    @PutMapping("/{id}")
    public ApiResponse<CompanyResponse> updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCompanyRequest request
    ) {
        return ApiResponse.success(companyService.updateCompany(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCompany(@PathVariable UUID id) {
        companyService.deleteCompany(id);
        return ApiResponse.success("Company deleted");
    }
}
