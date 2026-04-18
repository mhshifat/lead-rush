package com.leadrush.email.service;

import com.leadrush.company.entity.Company;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.ContactEmail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmailTemplateService's variable replacement.
 *
 * We only need to test the pure function `replaceVariables`,
 * so we construct the service with null deps — the method doesn't use them.
 */
class EmailTemplateServiceTest {

    private final EmailTemplateService service = new EmailTemplateService(null);

    @Test
    void replacesFirstNameAndCompanyName() {
        Contact contact = buildContact("John", "Doe", "VP of Sales", "Acme Corp", "john@acme.com");

        String result = service.replaceVariables(
                "Hi {{firstName}}, I saw you work at {{companyName}} as {{title}}.",
                contact
        );

        assertEquals("Hi John, I saw you work at Acme Corp as VP of Sales.", result);
    }

    @Test
    void usesFullNameTemplate() {
        Contact contact = buildContact("Jane", "Smith", null, null, "jane@test.com");

        String result = service.replaceVariables("Hello {{fullName}}!", contact);

        assertEquals("Hello Jane Smith!", result);
    }

    @Test
    void leavesPlaceholderWhenValueIsNull() {
        // When lastName is null, {{lastName}} should be left AS-IS
        // so the user can see which variable didn't resolve
        Contact contact = buildContact("John", null, null, null, "john@test.com");

        String result = service.replaceVariables("Hi {{firstName}} {{lastName}}!", contact);

        // {{firstName}} → "John", {{lastName}} → stays literal
        assertTrue(result.startsWith("Hi John"), "firstName should be replaced");
        assertTrue(result.contains("{{lastName}}"), "null lastName should be left as placeholder");
    }

    @Test
    void handlesNullTemplate() {
        Contact contact = buildContact("John", "Doe", null, null, "john@test.com");
        assertNull(service.replaceVariables(null, contact));
    }

    @Test
    void handlesContactWithoutCompany() {
        Contact contact = buildContact("John", "Doe", "Engineer", null, "john@test.com");

        String result = service.replaceVariables(
                "Hi {{firstName}} at {{companyName}}",
                contact
        );

        // No company → {{companyName}} stays as placeholder
        assertTrue(result.startsWith("Hi John"));
        assertTrue(result.contains("{{companyName}}"));
    }

    // ── Helpers ──

    private Contact buildContact(
            String firstName, String lastName, String title,
            String companyName, String email
    ) {
        Contact contact = Contact.builder()
                .firstName(firstName)
                .lastName(lastName)
                .title(title)
                .build();

        if (companyName != null) {
            Company c = Company.builder().name(companyName).build();
            contact.setCompany(c);
        }

        if (email != null) {
            ContactEmail e = ContactEmail.builder().email(email).primary(true).build();
            contact.addEmail(e);
        }

        return contact;
    }
}
