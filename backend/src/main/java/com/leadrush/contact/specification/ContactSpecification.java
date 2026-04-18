package com.leadrush.contact.specification;

import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.LifecycleStage;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/** Composable Specifications for dynamic contact filtering. */
public class ContactSpecification {

    private ContactSpecification() {}

    public static Specification<Contact> inWorkspace(UUID workspaceId) {
        return (root, query, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
    }

    public static Specification<Contact> nameContains(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern)
            );
        };
    }

    public static Specification<Contact> hasStage(LifecycleStage stage) {
        return (root, query, cb) -> cb.equal(root.get("lifecycleStage"), stage);
    }

    public static Specification<Contact> hasMinScore(int minScore) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("leadScore"), minScore);
    }

    public static Specification<Contact> inCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }

    public static Specification<Contact> hasSource(String source) {
        return (root, query, cb) -> cb.equal(root.get("source"), source);
    }
}
