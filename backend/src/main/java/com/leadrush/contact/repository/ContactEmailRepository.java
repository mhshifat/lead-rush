package com.leadrush.contact.repository;

import com.leadrush.contact.entity.ContactEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContactEmailRepository extends JpaRepository<ContactEmail, UUID> {

    List<ContactEmail> findByContactId(UUID contactId);
}
