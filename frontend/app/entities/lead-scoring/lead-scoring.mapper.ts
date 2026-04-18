import type {
  LeadScoreRuleApiDto,
  LeadScoreLogApiDto,
} from '~/types/api/lead-scoring.dto'
import type {
  LeadScoreRuleEntity,
  LeadScoreLogEntity,
  TriggerType,
  ConditionOperator,
} from './lead-scoring.entity'

export const LeadScoringMapper = {
  toRuleEntity(dto: LeadScoreRuleApiDto): LeadScoreRuleEntity {
    return {
      id: dto.id,
      name: dto.name,
      description: dto.description,
      triggerType: dto.triggerType as TriggerType,
      conditionField: dto.conditionField,
      conditionOperator: (dto.conditionOperator as ConditionOperator | null) ?? null,
      conditionValue: dto.conditionValue,
      points: dto.points,
      enabled: dto.enabled,
      createdAt: new Date(dto.createdAt),
      updatedAt: new Date(dto.updatedAt),
    }
  },

  toRuleEntityList(dtos: LeadScoreRuleApiDto[]): LeadScoreRuleEntity[] {
    return dtos.map(LeadScoringMapper.toRuleEntity)
  },

  toLogEntity(dto: LeadScoreLogApiDto): LeadScoreLogEntity {
    return {
      id: dto.id,
      contactId: dto.contactId,
      ruleId: dto.ruleId,
      ruleName: dto.ruleName,
      pointsDelta: dto.pointsDelta,
      scoreBefore: dto.scoreBefore,
      scoreAfter: dto.scoreAfter,
      triggerType: (dto.triggerType as TriggerType | null) ?? null,
      reason: dto.reason,
      createdAt: new Date(dto.createdAt),
    }
  },

  toLogEntityList(dtos: LeadScoreLogApiDto[]): LeadScoreLogEntity[] {
    return dtos.map(LeadScoringMapper.toLogEntity)
  },
}
