import type { ContactApiDto, CreateContactDto } from '~/types/api/contact.dto'
import type { ContactEntity, LifecycleStage, ContactSource } from './contact.entity'

/**
 * Contact Mapper — converts backend ContactApiDto ↔ frontend ContactEntity.
 *
 * This is the SINGLE place that knows the backend shape.
 * If the backend renames a field, we change this file only.
 * Zero changes needed in components, composables, or stores.
 */
export const ContactMapper = {
  toEntity(dto: ContactApiDto): ContactEntity {
    return {
      id: dto.id,
      firstName: dto.firstName,
      lastName: dto.lastName,
      fullName: dto.fullName,
      title: dto.title,

      companyId: dto.companyId,
      companyName: dto.companyName,

      lifecycleStage: (dto.lifecycleStage ?? 'LEAD') as LifecycleStage,
      leadScore: dto.leadScore,
      source: dto.source as ContactSource | null,

      avatarUrl: dto.avatarUrl,
      website: dto.website,
      linkedinUrl: dto.linkedinUrl,
      twitterUrl: dto.twitterUrl,

      primaryEmail: dto.primaryEmail,
      primaryPhone: dto.primaryPhone,

      emails: dto.emails.map(e => ({
        id: e.id,
        email: e.email,
        emailType: e.emailType as ContactEmailEntity['emailType'],
        isPrimary: e.primary,
        verificationStatus: e.verificationStatus as ContactEmailEntity['verificationStatus'],
      })),

      phones: dto.phones.map(p => ({
        id: p.id,
        phone: p.phone,
        phoneType: p.phoneType as ContactPhoneEntity['phoneType'],
        isPrimary: p.primary,
      })),

      tags: dto.tags.map(t => ({
        id: t.id,
        name: t.name,
        color: t.color,
      })),

      lastContactedAt: dto.lastContactedAt ? new Date(dto.lastContactedAt) : null,
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toEntityList(dtos: ContactApiDto[]): ContactEntity[] {
    return dtos.map(ContactMapper.toEntity)
  },

  /**
   * Convert a partial ContactEntity to the create DTO the backend expects.
   */
  toCreateDto(entity: Partial<ContactEntity> & {
    firstName: string
    companyName?: string
    newEmails?: Array<{ email: string; emailType?: string; primary?: boolean }>
    newPhones?: Array<{ phone: string; phoneType?: string; primary?: boolean }>
    newTags?: string[]
  }): CreateContactDto {
    return {
      firstName: entity.firstName,
      lastName: entity.lastName ?? undefined,
      title: entity.title ?? undefined,
      companyName: entity.companyName ?? undefined,
      lifecycleStage: entity.lifecycleStage ?? undefined,
      source: entity.source ?? undefined,
      avatarUrl: entity.avatarUrl ?? undefined,
      website: entity.website ?? undefined,
      linkedinUrl: entity.linkedinUrl ?? undefined,
      twitterUrl: entity.twitterUrl ?? undefined,
      emails: entity.newEmails,
      phones: entity.newPhones,
      tags: entity.newTags,
    }
  },
}

// Type imports for the nested types (keeps the mapper self-contained)
import type { ContactEmailEntity, ContactPhoneEntity } from './contact.entity'
