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

    /**
     * Adapter key that produced this email (HUNTER, GITHUB, PATTERN_CACHE, ...).
     * Null for user-entered addresses. Lets the contact page say
     * "Hunter — verified" vs "Pattern guess — unverified".
     */
    @Column(name = "source")
    private String source;

    public enum EmailType { WORK, PERSONAL, OTHER }

    /**
     * Email-address quality signal. Wider than the classic VALID/INVALID pair
     * because most enrichment adapters produce something in between — a
     * qualitative level lets us render honest UI ("Likely" chip, "Guessed" chip).
     *
     * <ul>
     *   <li><b>VERIFIED</b> — adapter confirmed deliverability (Hunter ≥90, PDL likelihood ≥9)</li>
     *   <li><b>LIKELY</b> — adapter had decent confidence but didn't verify</li>
     *   <li><b>UNKNOWN</b> — found somewhere with no verification signal</li>
     *   <li><b>GUESSED</b> — pattern construction, never observed</li>
     *   <li><b>VALID</b> — legacy alias kept for pre-existing rows; treat as VERIFIED</li>
     *   <li><b>INVALID</b> — adapter said this address doesn't accept mail</li>
     *   <li><b>CATCH_ALL</b> — domain accepts anything; deliverability unknown</li>
     * </ul>
     */
    public enum VerificationStatus {
        VERIFIED, LIKELY, UNKNOWN, GUESSED,
        VALID, INVALID, CATCH_ALL
    }
}
