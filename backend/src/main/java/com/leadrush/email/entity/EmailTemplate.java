package com.leadrush.email.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Email template — reusable template with variable placeholders.
 *
 * Template variables (replaced at send time with contact data):
 *   {{firstName}}    → contact.firstName
 *   {{lastName}}     → contact.lastName
 *   {{fullName}}     → contact.fullName
 *   {{companyName}}  → contact.company.name
 *   {{title}}        → contact.title
 *   {{email}}        → contact.primaryEmail
 */
@Entity
@Table(name = "email_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "text")
    private String bodyText;
}
