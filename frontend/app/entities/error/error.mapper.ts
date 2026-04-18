import type { ErrorApiDto } from '~/types/api/error.dto'
import type { AppError } from './error.entity'

export const ErrorMapper = {
  toEntity(dto: ErrorApiDto): AppError {
    const fieldErrors = dto.error.details?.reduce((acc, detail) => {
      acc[detail.field] = detail.message
      return acc
    }, {} as Record<string, string>)

    return {
      category: dto.error.category,
      message: dto.error.message,
      correlationId: dto.error.correlationId,
      fieldErrors: fieldErrors && Object.keys(fieldErrors).length > 0 ? fieldErrors : undefined,
      lastActivationEmailSentAt: dto.error.lastActivationEmailSentAt,
      isSystemError: dto.error.category === 'SYSTEM',
    }
  },
}
