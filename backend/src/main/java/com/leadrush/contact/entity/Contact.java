package com.leadrush.contact.entity;

import com.leadrush.common.TenantEntity;
import com.leadrush.company.entity.Company;
import com.leadrush.tag.entity.Tag;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.*;

/** Contact — the core Lead Rush entity (person / lead / prospect / customer). */
@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"company", "emails", "phones", "tags"})
public class Contact extends TenantEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_stage")
    @Builder.Default
    private LifecycleStage lifecycleStage = LifecycleStage.LEAD;

    @Column(name = "lead_score")
    @Builder.Default
    private int leadScore = 0;

    @Enumerated(EnumType.STRING)
    private ContactSource source;

    private String avatarUrl;
    private String website;
    private String linkedinUrl;
    private String twitterUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    @Column(name = "last_contacted_at")
    private LocalDateTime lastContactedAt;

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContactEmail> emails = new ArrayList<>();

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContactPhone> phones = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "contact_tags",
        joinColumns = @JoinColumn(name = "contact_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    public String getFullName() {
        if (lastName == null || lastName.isBlank()) return firstName;
        return firstName + " " + lastName;
    }

    public String getPrimaryEmail() {
        return emails.stream()
                .filter(ContactEmail::isPrimary)
                .findFirst()
                .or(() -> emails.stream().findFirst())
                .map(ContactEmail::getEmail)
                .orElse(null);
    }

    public void addEmail(ContactEmail email) {
        emails.add(email);
        email.setContact(this);
    }

    public void addPhone(ContactPhone phone) {
        phones.add(phone);
        phone.setContact(this);
    }
}
