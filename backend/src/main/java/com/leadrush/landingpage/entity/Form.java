package com.leadrush.landingpage.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Form definition — a schema of fields that gets rendered on a landing page or embedded widget.
 *
 * The "fields" column is a JSON array of field objects:
 *   [{ "key": "firstName", "label": "First Name", "type": "text", "required": true },
 *    { "key": "email", "label": "Email", "type": "email", "required": true },
 *    { "key": "message", "label": "Message", "type": "textarea" }]
 *
 * Field types: text, email, tel, textarea, select, checkbox
 */
@Entity
@Table(name = "forms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Form extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** JSON array of field definitions. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String fields = "[]";

    @Column(name = "success_redirect_url", length = 500)
    private String successRedirectUrl;

    @Column(name = "success_message", columnDefinition = "text")
    @Builder.Default
    private String successMessage = "Thank you! We'll be in touch.";

    /** Optional: auto-enroll the submitter in this sequence. */
    @Column(name = "auto_enroll_sequence_id")
    private UUID autoEnrollSequenceId;
}
