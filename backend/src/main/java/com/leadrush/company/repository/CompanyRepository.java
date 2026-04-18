package com.leadrush.company.repository;

import com.leadrush.company.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID>,
                                           JpaSpecificationExecutor<Company> {

    Page<Company> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Optional<Company> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Company> findByWorkspaceIdAndDomain(UUID workspaceId, String domain);
}
