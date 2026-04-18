package com.leadrush.contact.mapper;

import com.leadrush.contact.dto.ContactResponse;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.ContactEmail;
import com.leadrush.contact.entity.ContactPhone;
import com.leadrush.tag.entity.Tag;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface ContactMapper {

    @Mapping(target = "fullName", expression = "java(contact.getFullName())")
    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "companyName", source = "company.name")
    @Mapping(target = "primaryEmail", expression = "java(contact.getPrimaryEmail())")
    @Mapping(target = "primaryPhone", expression = "java(getPrimaryPhone(contact))")
    @Mapping(target = "lifecycleStage", expression = "java(contact.getLifecycleStage() != null ? contact.getLifecycleStage().name() : null)")
    @Mapping(target = "source", expression = "java(contact.getSource() != null ? contact.getSource().name() : null)")
    ContactResponse toResponse(Contact contact);

    List<ContactResponse> toResponseList(List<Contact> contacts);

    @Mapping(target = "emailType", expression = "java(email.getEmailType().name())")
    @Mapping(target = "verificationStatus", expression = "java(email.getVerificationStatus().name())")
    ContactResponse.EmailResponse toEmailResponse(ContactEmail email);

    @Mapping(target = "phoneType", expression = "java(phone.getPhoneType().name())")
    ContactResponse.PhoneResponse toPhoneResponse(ContactPhone phone);

    ContactResponse.TagResponse toTagResponse(Tag tag);

    Set<ContactResponse.TagResponse> toTagResponseSet(Set<Tag> tags);

    default String getPrimaryPhone(Contact contact) {
        return contact.getPhones().stream()
                .filter(ContactPhone::isPrimary)
                .findFirst()
                .or(() -> contact.getPhones().stream().findFirst())
                .map(ContactPhone::getPhone)
                .orElse(null);
    }
}
