package com.leadrush.contact.service;

import com.leadrush.common.exception.*;
import com.leadrush.company.entity.Company;
import com.leadrush.company.repository.CompanyRepository;
import com.leadrush.contact.dto.*;
import com.leadrush.contact.entity.*;
import com.leadrush.contact.mapper.ContactMapper;
import com.leadrush.contact.repository.*;
import com.leadrush.contact.specification.ContactSpecification;
import com.leadrush.leadscoring.entity.TriggerType;
import com.leadrush.leadscoring.service.LeadScoringService;
import com.leadrush.security.TenantContext;
import com.leadrush.tag.entity.Tag;
import com.leadrush.tag.repository.TagRepository;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final TagRepository tagRepository;
    private final ContactMapper contactMapper;
    private final LeadScoringService leadScoringService;
    private final WebhookService webhookService;

    @Transactional(readOnly = true)
    public Page<ContactResponse> listContacts(
            String search,
            String lifecycleStage,
            UUID companyId,
            Integer minScore,
            Pageable pageable
    ) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Specification<Contact> spec = Specification.where(
            ContactSpecification.inWorkspace(workspaceId)
        );

        if (search != null && !search.isBlank()) {
            spec = spec.and(ContactSpecification.nameContains(search));
        }
        if (lifecycleStage != null && !lifecycleStage.isBlank()) {
            spec = spec.and(ContactSpecification.hasStage(LifecycleStage.valueOf(lifecycleStage)));
        }
        if (companyId != null) {
            spec = spec.and(ContactSpecification.inCompany(companyId));
        }
        if (minScore != null) {
            spec = spec.and(ContactSpecification.hasMinScore(minScore));
        }

        return contactRepository.findAll(spec, pageable)
                .map(contactMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ContactResponse getContact(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Contact contact = contactRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", id));

        return contactMapper.toResponse(contact);
    }

    @Transactional
    public ContactResponse createContact(CreateContactRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Contact contact = new Contact();
        contact.setWorkspaceId(workspaceId);
        contact.setFirstName(request.getFirstName());
        contact.setLastName(request.getLastName());
        contact.setTitle(request.getTitle());
        contact.setAvatarUrl(request.getAvatarUrl());
        contact.setWebsite(request.getWebsite());
        contact.setLinkedinUrl(request.getLinkedinUrl());
        contact.setTwitterUrl(request.getTwitterUrl());

        if (request.getLifecycleStage() != null) {
            contact.setLifecycleStage(LifecycleStage.valueOf(request.getLifecycleStage()));
        }
        if (request.getSource() != null) {
            contact.setSource(ContactSource.valueOf(request.getSource()));
        } else {
            contact.setSource(ContactSource.MANUAL);
        }

        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            Company company = findOrCreateCompany(workspaceId, request.getCompanyName());
            contact.setCompany(company);
        }

        if (request.getEmails() != null) {
            for (CreateContactRequest.EmailDto emailDto : request.getEmails()) {
                ContactEmail email = ContactEmail.builder()
                        .email(emailDto.getEmail())
                        .emailType(emailDto.getEmailType() != null
                                ? ContactEmail.EmailType.valueOf(emailDto.getEmailType())
                                : ContactEmail.EmailType.WORK)
                        .primary(emailDto.isPrimary())
                        .build();
                email.setWorkspaceId(workspaceId);
                contact.addEmail(email);
            }
        }

        if (request.getPhones() != null) {
            for (CreateContactRequest.PhoneDto phoneDto : request.getPhones()) {
                ContactPhone phone = ContactPhone.builder()
                        .phone(phoneDto.getPhone())
                        .phoneType(phoneDto.getPhoneType() != null
                                ? ContactPhone.PhoneType.valueOf(phoneDto.getPhoneType())
                                : ContactPhone.PhoneType.WORK)
                        .primary(phoneDto.isPrimary())
                        .build();
                phone.setWorkspaceId(workspaceId);
                contact.addPhone(phone);
            }
        }

        if (request.getTags() != null) {
            for (String tagName : request.getTags()) {
                Tag tag = tagRepository.findByWorkspaceIdAndName(workspaceId, tagName)
                        .orElseGet(() -> {
                            Tag newTag = Tag.builder().name(tagName).build();
                            newTag.setWorkspaceId(workspaceId);
                            return tagRepository.save(newTag);
                        });
                contact.getTags().add(tag);
            }
        }

        contact = contactRepository.save(contact);
        log.info("Contact created: {} (id: {})", contact.getFullName(), contact.getId());

        leadScoringService.fireTrigger(TriggerType.CONTACT_CREATED, contact.getId(),
                "Contact created: " + contact.getFullName());

        webhookService.publish(WebhookEventType.CONTACT_CREATED, java.util.Map.of(
                "contactId", contact.getId(),
                "fullName", contact.getFullName(),
                "email", contact.getPrimaryEmail() != null ? contact.getPrimaryEmail() : "",
                "companyName", contact.getCompany() != null ? contact.getCompany().getName() : "",
                "lifecycleStage", contact.getLifecycleStage().name(),
                "source", contact.getSource() != null ? contact.getSource().name() : ""
        ));

        return contactMapper.toResponse(contact);
    }

    @Transactional
    public ContactResponse updateContact(UUID id, UpdateContactRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Contact contact = contactRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", id));

        LifecycleStage previousStage = contact.getLifecycleStage();
        if (request.getFirstName() != null) contact.setFirstName(request.getFirstName());
        if (request.getLastName() != null) contact.setLastName(request.getLastName());
        if (request.getTitle() != null) contact.setTitle(request.getTitle());
        if (request.getLifecycleStage() != null) {
            contact.setLifecycleStage(LifecycleStage.valueOf(request.getLifecycleStage()));
        }
        if (request.getAvatarUrl() != null) contact.setAvatarUrl(request.getAvatarUrl());
        if (request.getWebsite() != null) contact.setWebsite(request.getWebsite());
        if (request.getLinkedinUrl() != null) contact.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getTwitterUrl() != null) contact.setTwitterUrl(request.getTwitterUrl());

        contact = contactRepository.save(contact);

        if (contact.getLifecycleStage() != previousStage) {
            leadScoringService.fireTrigger(TriggerType.CONTACT_UPDATED, contact.getId(),
                    "Lifecycle stage changed: " + previousStage + " → " + contact.getLifecycleStage());

            webhookService.publish(WebhookEventType.CONTACT_UPDATED, java.util.Map.of(
                    "contactId", contact.getId(),
                    "fullName", contact.getFullName(),
                    "previousLifecycleStage", previousStage != null ? previousStage.name() : "",
                    "lifecycleStage", contact.getLifecycleStage().name()
            ));
        }

        return contactMapper.toResponse(contact);
    }

    @Transactional
    public void deleteContact(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Contact contact = contactRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", id));

        contactRepository.delete(contact);
        log.info("Contact deleted: {} (id: {})", contact.getFullName(), id);
    }

    private Company findOrCreateCompany(UUID workspaceId, String companyName) {
        return companyRepository.findByWorkspaceId(workspaceId, Pageable.unpaged())
                .stream()
                .filter(c -> c.getName().equalsIgnoreCase(companyName))
                .findFirst()
                .orElseGet(() -> {
                    Company newCompany = Company.builder()
                            .name(companyName)
                            .build();
                    newCompany.setWorkspaceId(workspaceId);
                    return companyRepository.save(newCompany);
                });
    }
}
