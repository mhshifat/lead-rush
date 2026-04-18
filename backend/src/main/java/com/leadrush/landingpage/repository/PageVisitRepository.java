package com.leadrush.landingpage.repository;

import com.leadrush.landingpage.entity.PageVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PageVisitRepository extends JpaRepository<PageVisit, UUID> {
}
