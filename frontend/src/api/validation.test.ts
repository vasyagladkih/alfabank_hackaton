import { describe, expect, it } from 'vitest';
import { application, applicationDetails, applicationPage, approvedApplication, clientSession, paymentSchedule } from '../test/fixtures';
import { isApplication, isApplicationDetails, isApplicationPage, isPaymentSchedule, normalizeApplicationDetails } from './validation';

describe('application response validation', () => {
  it('accepts the backend counteroffer comment on an approved application', () => {
    expect(isApplication({ ...approvedApplication, amount: 1500000, approvedAmount: 100000,
      rejectionReason: 'Сформировано встречное предложение: одобрен лимит 100000 ₽' })).toBe(true);
  });

  it('accepts preliminary terms and a dossier schedule during borderline manual review', () => {
    const preliminary = { ...applicationDetails, status: 'MANUAL_REVIEW', reviewReasonCode: 'BORDERLINE_SCORE',
      canRetry: false, approvedAmount: 100000, approvedRate: 18.5, schedule: paymentSchedule };
    expect(isApplication(preliminary)).toBe(true);
    expect(isApplicationDetails(preliminary)).toBe(true);
  });

  it.each(['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED', 'REJECTED'])('accepts the %s status', (status) => {
    expect(isApplication({ ...application, status })).toBe(true);
  });

  it.each([{ status: 'UNKNOWN' }, { status: null }, { status: ['SIGNED'] }])('rejects invalid status %j', ({ status }) => {
    expect(isApplication({ ...application, status })).toBe(false);
  });

  it.each([undefined, null, 0, 1200000])('accepts nullable approved amount %s', (approvedAmount) => {
    expect(isApplication({ ...application, approvedAmount })).toBe(true);
  });

  it.each([undefined, null, 0, 18.5])('accepts nullable approved rate %s', (approvedRate) => {
    expect(isApplication({ ...application, approvedRate })).toBe(true);
  });

  it.each([
    { approvedAmount: 1.5 }, { approvedAmount: Number.MAX_SAFE_INTEGER + 1 }, { approvedAmount: '100000' },
    { approvedRate: '18.5' }, { approvedRate: Infinity }, { approvedRate: NaN },
    { schedule: [] }, { schedule: {} }, { schedule: { ...paymentSchedule, items: [null] } },
    { purpose: ['EQUIPMENT'] },
  ])('rejects malformed loan terms %j', (invalid) => {
    expect(isApplication({ ...approvedApplication, ...invalid })).toBe(false);
  });
});

describe('payment schedule response validation', () => {
  it('accepts a complete schedule and an empty items array', () => {
    expect(isPaymentSchedule(paymentSchedule)).toBe(true);
    expect(isPaymentSchedule({ ...paymentSchedule, items: [] })).toBe(true);
  });

  it.each(['monthlyPayment', 'totalInterest', 'totalPayout', 'items'])('requires schedule field %s', (field) => {
    const incomplete: Record<string, unknown> = { ...paymentSchedule };
    delete incomplete[field];
    expect(isPaymentSchedule(incomplete)).toBe(false);
  });

  it.each(['monthNumber', 'paymentDate', 'totalPayment', 'principalPayment', 'interestPayment', 'remainingDebt'])('requires item field %s', (field) => {
    const incomplete: Record<string, unknown> = { ...paymentSchedule.items[0] };
    delete incomplete[field];
    expect(isPaymentSchedule({ ...paymentSchedule, items: [incomplete] })).toBe(false);
  });

  it.each(['monthlyPayment', 'totalInterest', 'totalPayout'])('requires safe integer money in %s', (field) => {
    for (const invalid of [1.5, Number.MAX_SAFE_INTEGER + 1, '100', null, Infinity]) {
      expect(isPaymentSchedule({ ...paymentSchedule, [field]: invalid })).toBe(false);
    }
  });

  it.each(['monthNumber', 'totalPayment', 'principalPayment', 'interestPayment', 'remainingDebt'])('requires a safe integer item %s', (field) => {
    for (const invalid of [1.5, Number.MAX_SAFE_INTEGER + 1, '100', null, Infinity]) {
      expect(isPaymentSchedule({ ...paymentSchedule, items: [{ ...paymentSchedule.items[0], [field]: invalid }] })).toBe(false);
    }
  });

  it.each(['2026-02-29', '2026-02-30', '2026-13-01', '2026-11-22T00:00:00Z', '2026-1-1', ''])('rejects invalid date-only payment %s', (paymentDate) => {
    expect(isPaymentSchedule({ ...paymentSchedule, items: [{ ...paymentSchedule.items[0], paymentDate }] })).toBe(false);
  });

  it('accepts a real leap day', () => {
    expect(isPaymentSchedule({ ...paymentSchedule, items: [{ ...paymentSchedule.items[0], paymentDate: '2024-02-29' }] })).toBe(true);
  });
});

describe('employee dossier response validation', () => {
  it('accepts a complete dossier or only its required application fields', () => {
    expect(isApplicationDetails(applicationDetails)).toBe(true);
    expect(isApplicationDetails(application)).toBe(true);
  });

  it('normalizes absent optional Java containers and scalars without weakening required fields', () => {
    const normalized = normalizeApplicationDetails({
      ...application, companyName: null, inn: null, updatedAt: null, schedule: null,
      reviewReasonCode: null, canRetry: null, user: null, fns: null, bki: null, scoring: null,
      approvedAmount: null, approvedRate: null,
    });
    expect(isApplicationDetails(normalized)).toBe(true);
    for (const field of ['companyName', 'inn', 'updatedAt', 'schedule', 'reviewReasonCode', 'canRetry', 'user', 'fns', 'bki', 'scoring']) {
      expect(normalized).not.toHaveProperty(field);
    }
    expect(normalized).toMatchObject({ approvedAmount: null, approvedRate: null, rejectionReason: null });
    for (const field of ['id', 'amount', 'termMonths', 'purpose', 'status', 'createdAt']) {
      expect(isApplicationDetails(normalizeApplicationDetails({ ...applicationDetails, [field]: null }))).toBe(false);
    }
  });

  it('normalizes optional snapshot nulls while preserving false and zero', () => {
    const raw = {
      ...applicationDetails, canRetry: false,
      user: { ...clientSession.user, companyName: null, inn: null },
      fns: { inn: null, companyName: null, stateStatus: null, taxBlocksExist: false, businessAgeMonths: 0, createdAt: null },
      bki: { inn: null, bkiScore: 0, monthlyPaymentRub: 0, currentOverdueDays: 0, cntOverdueOver90d: 0, riskBucket: null, createdAt: null },
      scoring: { scoringScore: 0, calculatedPdnPercent: 0, maxLimitRub: null },
    };
    const normalized = normalizeApplicationDetails(raw);
    expect(isApplicationDetails(normalized)).toBe(true);
    expect(normalized).toMatchObject({
      canRetry: false, user: { id: clientSession.user.id, username: clientSession.user.username, role: 'CLIENT' },
      fns: { taxBlocksExist: false, businessAgeMonths: 0 },
      bki: { bkiScore: 0, monthlyPaymentRub: 0, currentOverdueDays: 0, cntOverdueOver90d: 0 },
      scoring: { scoringScore: 0, calculatedPdnPercent: 0 },
    });
    expect(normalized).not.toHaveProperty('user.companyName');
    expect(normalized).not.toHaveProperty('fns.createdAt');
    expect(normalized).not.toHaveProperty('bki.riskBucket');
    expect(normalized).not.toHaveProperty('scoring.maxLimitRub');
    expect(raw.user.companyName).toBeNull();
  });

  it.each([
    { reviewReasonCode: 'OTHER' }, { canRetry: 0 }, { user: {} }, { user: { ...clientSession.user, id: null } },
    { fns: [] }, { fns: { taxBlocksExist: 'false' } }, { fns: { businessAgeMonths: 1.5 } }, { fns: { createdAt: 'bad' } },
    { bki: { monthlyPaymentRub: '0' } }, { bki: { currentOverdueDays: 0.5 } }, { bki: { riskBucket: false } },
    { scoring: [] }, { scoring: { scoringScore: '85' } }, { scoring: { calculatedPdnPercent: Infinity } }, { scoring: { maxLimitRub: Number.MAX_SAFE_INTEGER + 1 } },
    { schedule: { ...paymentSchedule, items: [{}] } },
  ])('rejects malformed present dossier fields %j', (invalid) => {
    expect(isApplicationDetails(normalizeApplicationDetails({ ...applicationDetails, ...invalid }))).toBe(false);
  });
});

describe('employee page response validation', () => {
  it('accepts populated and empty pages', () => {
    expect(isApplicationPage(applicationPage)).toBe(true);
    expect(isApplicationPage({ ...applicationPage, items: [], totalElements: 0, totalPages: 0 })).toBe(true);
  });

  it.each(['items', 'page', 'size', 'totalElements', 'totalPages'])('requires %s', (field) => {
    const incomplete: Record<string, unknown> = { ...applicationPage };
    delete incomplete[field];
    expect(isApplicationPage(incomplete)).toBe(false);
  });

  it.each([
    { page: -1 }, { page: 0.5 }, { page: '0' },
    { size: 0 }, { size: -1 }, { size: 1.5 },
    { totalElements: -1 }, { totalElements: 1.5 }, { totalElements: Number.MAX_SAFE_INTEGER + 1 },
    { totalPages: -1 }, { totalPages: 1.5 }, { totalPages: null },
    { items: null }, { items: [{ ...application, status: 'UNKNOWN' }] },
  ])('rejects invalid page fields %j', (invalid) => {
    expect(isApplicationPage({ ...applicationPage, ...invalid })).toBe(false);
  });

  it('rejects the old array response', () => {
    expect(isApplicationPage([application])).toBe(false);
  });
});
