package com.leadrush.email.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailLinkClickRepository extends JpaRepository<EmailLinkClick, UUID> {
}
