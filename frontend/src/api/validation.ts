import type { Application, Session, UserProfile } from './types';
import { APPLICATION_STATUSES } from '../features/applications/model';

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

function isDecimal(value: unknown): value is string {
  return typeof value === 'string' && /^\d+(?:\.\d+)?$/.test(value) && Number.isFinite(Number(value));
}

function isOffer(value: unknown): boolean {
  return isRecord(value)
    && typeof value.amount === 'number' && Number.isSafeInteger(value.amount) && value.amount >= 100000 && value.amount <= 50000000
    && typeof value.termMonths === 'number' && Number.isSafeInteger(value.termMonths) && value.termMonths >= 1 && value.termMonths <= 60
    && isDecimal(value.annualRate) && isDecimal(value.monthlyPayment);
}

function isDecisionSummary(value: unknown): boolean {
  return isRecord(value)
    && typeof value.ruleVersion === 'string' && value.ruleVersion.trim().length > 0
    && typeof value.assessedAt === 'string' && Number.isFinite(Date.parse(value.assessedAt))
    && typeof value.explanation === 'string'
    && value.demoOnly === true;
}

export function isApplication(value: unknown): value is Application {
  return isRecord(value)
    && Number.isSafeInteger(value.id)
    && Number.isSafeInteger(value.amount)
    && Number.isSafeInteger(value.termMonths)
    && typeof value.version === 'number' && Number.isSafeInteger(value.version) && value.version >= 0
    && ['WORKING_CAPITAL', 'EQUIPMENT', 'BUSINESS_EXPANSION', 'REFINANCING'].includes(String(value.purpose))
    && APPLICATION_STATUSES.some((status) => status === value.status)
    && typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt))
    && (value.updatedAt === undefined || (typeof value.updatedAt === 'string' && Number.isFinite(Date.parse(value.updatedAt))))
    && (value.companyName === undefined || typeof value.companyName === 'string')
    && (value.inn === undefined || typeof value.inn === 'string')
    && (value.rejectionReason == null || typeof value.rejectionReason === 'string')
    && (value.decisionSummary == null || isDecisionSummary(value.decisionSummary))
    && (value.offer == null || (isOffer(value.offer) && isRecord(value.offer) && Number(value.offer.amount) <= Number(value.amount)));
}

export function isApplicationList(value: unknown): value is Application[] {
  return Array.isArray(value) && value.every(isApplication);
}
