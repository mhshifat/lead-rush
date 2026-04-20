package com.leadrush.contact.repository;

import com.leadrush.contact.entity.ContactEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactEmailRepository extends JpaRepository<ContactEmail, UUID> {

    List<ContactEmail> findByContactId(UUID contactId);

    /**
     * Match any ContactEmail row by address (case-insensitive) inside the given
     * workspace. Used by the Gmail sidebar to find the contact for the currently
     * open thread — we match across all rows (work + personal + unverified)
     * because any match is signal, not just the primary.
     */
    @Query("""
            SELECT ce FROM ContactEmail ce
            WHERE ce.workspaceId = :workspaceId
              AND LOWER(ce.email) = LOWER(:email)
            ORDER BY ce.primary DESC
            """)
    List<ContactEmail> findAllByWorkspaceAndEmail(
            @Param("workspaceId") UUID workspaceId,
            @Param("email") String email
    );

    default Optional<ContactEmail> findFirstByWorkspaceIdAndEmailIgnoreCase(UUID workspaceId, String email) {
        return findAllByWorkspaceAndEmail(workspaceId, email).stream().findFirst();
    }
}
