package com.leadrush.contact.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contact_phones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"contact"})
public class ContactPhone extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "phone_type")
    @Builder.Default
    private PhoneType phoneType = PhoneType.WORK;

    @Column(name = "is_primary")
    @Builder.Default
    private boolean primary = false;

    public enum PhoneType { WORK, MOBILE, PERSONAL, OTHER }
}
