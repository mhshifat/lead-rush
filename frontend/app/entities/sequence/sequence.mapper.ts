import type {
  SequenceApiDto,
  StepApiDto,
  EnrollmentApiDto,
} from '~/types/api/sequence.dto'
import type {
  SequenceEntity,
  SequenceStepEntity,
  EnrollmentEntity,
  SequenceStatus,
  StepType,
  EnrollmentStatus,
} from './sequence.entity'

export const SequenceMapper = {
  toEntity(dto: SequenceApiDto): SequenceEntity {
    return {
      id: dto.id,
      name: dto.name,
      description: dto.description,
      status: dto.status as SequenceStatus,
      defaultMailboxId: dto.defaultMailboxId,
      defaultMailboxEmail: dto.defaultMailboxEmail,
      totalEnrolled: dto.totalEnrolled,
      totalCompleted: dto.totalCompleted,
      totalReplied: dto.totalReplied,
      steps: dto.steps.map(SequenceMapper.toStepEntity),
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toEntityList(dtos: SequenceApiDto[]): SequenceEntity[] {
    return dtos.map(SequenceMapper.toEntity)
  },

  toStepEntity(dto: StepApiDto): SequenceStepEntity {
    return {
      id: dto.id,
      stepOrder: dto.stepOrder,
      stepType: dto.stepType as StepType,
      delayDays: dto.delayDays,
      emailTemplateId: dto.emailTemplateId,
      emailTemplateName: dto.emailTemplateName,
      subjectOverride: dto.subjectOverride,
      bodyHtmlOverride: dto.bodyHtmlOverride,
      taskDescription: dto.taskDescription,
      skipIfPreviousOpened: dto.skipIfPreviousOpened,
      skipIfPreviousClicked: dto.skipIfPreviousClicked,
    }
  },

  toEnrollmentEntity(dto: EnrollmentApiDto): EnrollmentEntity {
    return {
      id: dto.id,
      sequenceId: dto.sequenceId,
      sequenceName: dto.sequenceName,
      contactId: dto.contactId,
      contactFullName: dto.contactFullName,
      contactEmail: dto.contactEmail,
      mailboxId: dto.mailboxId,
      mailboxEmail: dto.mailboxEmail,
      currentStepIndex: dto.currentStepIndex,
      nextExecutionAt: dto.nextExecutionAt ? new Date(dto.nextExecutionAt) : null,
      status: dto.status as EnrollmentStatus,
      enrolledAt: new Date(dto.enrolledAt),
      completedAt: dto.completedAt ? new Date(dto.completedAt) : null,
    }
  },
}
