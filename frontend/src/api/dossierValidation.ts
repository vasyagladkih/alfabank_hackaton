import type { EmployeeDossier } from './types';
import { isApplication } from './validation';
import { APPLICATION_STATUSES } from '../features/applications/model';

const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value);
const text = (value: unknown): value is string => typeof value === 'string';
const nonempty = (value: unknown): value is string => text(value) && value.trim().length > 0;
const integer = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
const optional = (value: unknown, validate: (item: unknown) => boolean) => value == null || validate(value);
const list = (value: unknown, validate: (item: unknown) => boolean) => Array.isArray(value) && value.every(validate);
const decimal = (value: unknown) => text(value) && /^-?\d+(?:\.\d+)?$/.test(value) && Number.isFinite(Number(value));
const dateTime = (value: unknown) => text(value) && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value));
const date = (value: unknown) => text(value) && /^\d{4}-\d{2}-\d{2}$/.test(value)
  && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
const status = (value: unknown) => APPLICATION_STATUSES.some((item) => item === value);
const oneOf = (value: unknown, choices: readonly string[]) => text(value) && choices.includes(value);
const action = (value: unknown) => oneOf(value, ['APPROVE', 'REJECT', 'COUNTEROFFER']);

function fact(value: unknown): boolean {
  return record(value) && nonempty(value.code) && nonempty(value.label) && nonempty(value.source)
    && optional(value.value, text) && optional(value.unit, text);
}

function source(value: unknown): boolean {
  return record(value) && nonempty(value.code) && nonempty(value.label) && value.demoOnly === true
    && oneOf(value.freshness, ['FRESH', 'STALE', 'MISSING', 'INVALID'])
    && optional(value.version, text) && optional(value.observedOn, date);
}

function contribution(value: unknown): boolean {
  return record(value) && nonempty(value.code) && nonempty(value.label) && text(value.value)
    && integer(value.points) && integer(value.maximum) && value.points <= value.maximum && text(value.explanation);
}

function finding(value: unknown): boolean {
  return record(value) && nonempty(value.code) && text(value.message)
    && oneOf(value.severity, ['HARD_STOP', 'DATA_QUALITY', 'WARNING', 'DECISION']);
}

function assessment(value: unknown): boolean {
  return record(value) && nonempty(value.ruleVersion) && date(value.asOf) && status(value.outcome)
    && optional(value.score, (score) => integer(score) && score <= 850) && integer(value.maxScore)
    && typeof value.evidenceComplete === 'boolean' && typeof value.eligibleForManualApproval === 'boolean'
    && ['annualRate', 'monthlyIncome', 'existingDebtPayment', 'requestedPayment', 'debtRatio'].every((key) => optional(value[key], decimal))
    && optional(value.affordableAmount, (amount) => integer(amount) && amount <= 50000000)
    && list(value.components, contribution) && list(value.findings, finding);
}

function auditEvent(value: unknown): boolean {
  return record(value) && integer(value.id) && nonempty(value.eventType)
    && oneOf(value.actorRole, ['SYSTEM', 'EMPLOYEE', 'CLIENT'])
    && optional(value.fromStatus, status) && status(value.toStatus)
    && optional(value.rationale, text) && dateTime(value.createdAt) && integer(value.version);
}

export function isEmployeeDossier(value: unknown): value is EmployeeDossier {
  return record(value) && isApplication(value.application) && value.demoOnly === true
    && optional(value.snapshotCapturedAt, dateTime) && optional(value.snapshotAsOf, date)
    && optional(value.snapshotOrigin, text) && optional(value.snapshotSha256, text)
    && list(value.facts, fact) && list(value.sources, source)
    && list(value.cashflows, (item) => record(item) && text(item.month) && /^\d{4}-(?:0[1-9]|1[0-2])$/.test(item.month) && optional(item.turnover, decimal))
    && optional(value.assessment, assessment) && list(value.allowedActions, action) && list(value.audit, auditEvent);
}
