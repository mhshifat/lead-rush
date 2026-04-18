package com.leadrush.contact.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contact_emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"contact"})
public class ContactEmail extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type")
    @Builder.Default
    private EmailType emailType = EmailType.WORK;

    @Column(name = "is_primary")
    @Builder.Default
    private boolean primary = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.UNKNOWN;

    public enum EmailType { WORK, PERSONAL, OTHER }
    public enum VerificationStatus { UNKNOWN, VALID, INVALID, CATCH_ALL }
}
