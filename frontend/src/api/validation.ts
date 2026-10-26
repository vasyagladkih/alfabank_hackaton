import type { Application, ApplicationDetails, ApplicationPage, PaymentSchedule, Session, UserProfile } from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isDateTime(value: unknown): value is string {
  return typeof value === 'string' && Number.isFinite(Date.parse(value));
}

function isDateOnly(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}

// Java serializes absent optional DTO fields as null; generated API types use absence.
function omitOptionalNulls(value: unknown, fields: readonly string[]): unknown {
  if (!isRecord(value)) return value;
  const normalized = { ...value };
  for (const field of fields) if (normalized[field] === null) delete normalized[field];
  return normalized;
}

export function normalizeApplication(value: unknown): unknown {
  return omitOptionalNulls(value, ['companyName', 'inn', 'updatedAt', 'schedule']);
}

export function normalizeApplicationList(value: unknown): unknown {
  return Array.isArray(value) ? value.map(normalizeApplication) : value;
}

export function normalizeApplicationPage(value: unknown): unknown {
  return isRecord(value) ? { ...value, items: normalizeApplicationList(value.items) } : value;
}

export function normalizeApplicationDetails(value: unknown): unknown {
  const details = omitOptionalNulls(normalizeApplication(value), ['reviewReasonCode', 'canRetry', 'user', 'fns', 'bki', 'scoring']);
  if (!isRecord(details)) return details;
  if (details.user !== undefined) details.user = omitOptionalNulls(details.user, ['companyName', 'inn']);
  if (details.fns !== undefined) details.fns = omitOptionalNulls(details.fns, ['inn', 'companyName', 'stateStatus', 'taxBlocksExist', 'businessAgeMonths', 'createdAt']);
  if (details.bki !== undefined) details.bki = omitOptionalNulls(details.bki, ['inn', 'bkiScore', 'monthlyPaymentRub', 'currentOverdueDays', 'cntOverdueOver90d', 'riskBucket', 'createdAt']);
  if (details.scoring !== undefined) details.scoring = omitOptionalNulls(details.scoring, ['scoringScore', 'calculatedPdnPercent', 'maxLimitRub']);
  return details;
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
    && typeof value.purpose === 'string' && ['WORKING_CAPITAL', 'EQUIPMENT', 'BUSINESS_EXPANSION', 'REFINANCING'].includes(value.purpose)
    && typeof value.status === 'string' && ['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED', 'REJECTED'].includes(value.status)
    && isDateTime(value.createdAt)
    && (value.updatedAt === undefined || isDateTime(value.updatedAt))
    && (value.companyName === undefined || typeof value.companyName === 'string')
    && (value.inn === undefined || typeof value.inn === 'string')
    && (value.rejectionReason == null || typeof value.rejectionReason === 'string')
    && (value.approvedAmount == null || Number.isSafeInteger(value.approvedAmount))
    && (value.approvedRate == null || (typeof value.approvedRate === 'number' && Number.isFinite(value.approvedRate)))
    && (value.schedule === undefined || isPaymentSchedule(value.schedule));
}

export function isPaymentSchedule(value: unknown): value is PaymentSchedule {
  return isRecord(value)
    && Number.isSafeInteger(value.monthlyPayment)
    && Number.isSafeInteger(value.totalInterest)
    && Number.isSafeInteger(value.totalPayout)
    && Array.isArray(value.items) && value.items.every((item: unknown) => isRecord(item)
      && Number.isSafeInteger(item.monthNumber)
      && isDateOnly(item.paymentDate)
      && Number.isSafeInteger(item.totalPayment)
      && Number.isSafeInteger(item.principalPayment)
      && Number.isSafeInteger(item.interestPayment)
      && Number.isSafeInteger(item.remainingDebt));
}

function isFnsSnapshot(value: unknown): boolean {
  return isRecord(value)
    && (value.inn === undefined || typeof value.inn === 'string')
    && (value.companyName === undefined || typeof value.companyName === 'string')
    && (value.stateStatus === undefined || typeof value.stateStatus === 'string')
    && (value.taxBlocksExist === undefined || typeof value.taxBlocksExist === 'boolean')
    && (value.businessAgeMonths === undefined || Number.isSafeInteger(value.businessAgeMonths))
    && (value.createdAt === undefined || isDateTime(value.createdAt));
}

function isBkiSnapshot(value: unknown): boolean {
  return isRecord(value)
    && (value.inn === undefined || typeof value.inn === 'string')
    && (value.bkiScore === undefined || Number.isSafeInteger(value.bkiScore))
    && (value.monthlyPaymentRub === undefined || Number.isSafeInteger(value.monthlyPaymentRub))
    && (value.currentOverdueDays === undefined || Number.isSafeInteger(value.currentOverdueDays))
    && (value.cntOverdueOver90d === undefined || Number.isSafeInteger(value.cntOverdueOver90d))
    && (value.riskBucket === undefined || typeof value.riskBucket === 'string')
    && (value.createdAt === undefined || isDateTime(value.createdAt));
}

function isScoringMetrics(value: unknown): boolean {
  return isRecord(value)
    && (value.scoringScore === undefined || Number.isSafeInteger(value.scoringScore))
    && (value.calculatedPdnPercent === undefined || (typeof value.calculatedPdnPercent === 'number' && Number.isFinite(value.calculatedPdnPercent)))
    && (value.maxLimitRub === undefined || Number.isSafeInteger(value.maxLimitRub));
}

export function isApplicationDetails(value: unknown): value is ApplicationDetails {
  return isRecord(value)
    && (value.reviewReasonCode === undefined || value.reviewReasonCode === 'TECH_FAILURE' || value.reviewReasonCode === 'BORDERLINE_SCORE')
    && (value.canRetry === undefined || typeof value.canRetry === 'boolean')
    && (value.user === undefined || isUserProfile(value.user))
    && (value.fns === undefined || isFnsSnapshot(value.fns))
    && (value.bki === undefined || isBkiSnapshot(value.bki))
    && (value.scoring === undefined || isScoringMetrics(value.scoring))
    && isApplication(value);
}

export function isApplicationList(value: unknown): value is Application[] {
  return Array.isArray(value) && value.every(isApplication);
}

export function isApplicationPage(value: unknown): value is ApplicationPage {
  return isRecord(value)
    && isApplicationList(value.items)
    && Number.isSafeInteger(value.page) && Number(value.page) >= 0
    && Number.isSafeInteger(value.size) && Number(value.size) > 0
    && Number.isSafeInteger(value.totalElements) && Number(value.totalElements) >= 0
    && Number.isSafeInteger(value.totalPages) && Number(value.totalPages) >= 0;
}
