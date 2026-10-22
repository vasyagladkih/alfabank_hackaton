import type { Application, Session, UserProfile } from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isUserProfile(value: unknown): value is UserProfile {
  return isRecord(value)
    && Number.isSafeInteger(value.id)
    && typeof value.username === 'string' && value.username.trim().length > 0
    && (value.role === 'CLIENT' || value.role === 'EMPLOYEE')
    && (value.companyName === undefined || typeof value.companyName === 'string')
    && (value.inn === undefined || typeof value.inn === 'string');
}

export function isSession(value: unknown): value is Session {
  return isRecord(value)
    && typeof value.token === 'string' && value.token.trim().length > 0
    && !/[\r\n]/.test(value.token)
    && isUserProfile(value.user);
}

export function isApplication(value: unknown): value is Application {
  return isRecord(value)
    && Number.isSafeInteger(value.id)
    && Number.isSafeInteger(value.amount)
    && Number.isSafeInteger(value.termMonths)
    && ['WORKING_CAPITAL', 'EQUIPMENT', 'BUSINESS_EXPANSION', 'REFINANCING'].includes(String(value.purpose))
    && ['PROCESSING', 'APPROVED', 'REJECTED'].includes(String(value.status))
    && typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt))
    && (value.updatedAt === undefined || (typeof value.updatedAt === 'string' && Number.isFinite(Date.parse(value.updatedAt))))
    && (value.companyName === undefined || typeof value.companyName === 'string')
    && (value.inn === undefined || typeof value.inn === 'string')
    && (value.rejectionReason == null || typeof value.rejectionReason === 'string');
}

export function isApplicationList(value: unknown): value is Application[] {
  return Array.isArray(value) && value.every(isApplication);
}
