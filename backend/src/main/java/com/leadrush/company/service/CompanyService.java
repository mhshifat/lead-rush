package com.leadrush.company.service;

import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.company.dto.*;
import com.leadrush.company.entity.Company;
import com.leadrush.company.mapper.CompanyMapper;
import com.leadrush.company.repository.CompanyRepository;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final CompanyMapper companyMapper;

    @Transactional(readOnly = true)
    public Page<CompanyResponse> listCompanies(String search, Pageable pageable) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Specification<Company> spec = (root, query, cb) ->
                cb.equal(root.get("workspaceId"), workspaceId);

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("domain")), pattern)
                    ));
        }

        return companyRepository.findAll(spec, pageable)
                .map(companyMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Company company = companyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));

        return companyMapper.toResponse(company);
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Company company = companyMapper.toEntity(request);
        company.setWorkspaceId(workspaceId);
        company = companyRepository.save(company);

        log.info("Company created: {} (id: {})", company.getName(), company.getId());
        return companyMapper.toResponse(company);
    }

    @Transactional
    public CompanyResponse updateCompany(UUID id, CreateCompanyRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Company company = companyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));

        companyMapper.updateEntity(request, company);
        company = companyRepository.save(company);

        return companyMapper.toResponse(company);
    }

    @Transactional
    public void deleteCompany(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Company company = companyRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));

        companyRepository.delete(company);
        log.info("Company deleted: {} (id: {})", company.getName(), id);
    }
}
