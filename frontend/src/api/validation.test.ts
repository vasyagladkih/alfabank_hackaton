import { describe, expect, it } from 'vitest';
import { application } from '../test/fixtures';
import { isApplication } from './validation';

const offer = { amount: 900000, termMonths: 12, annualRate: '18.50', monthlyPayment: '82724.12' };
const decisionSummary = { ruleVersion: 'demo-v1', assessedAt: '2026-10-23T12:00:00Z', explanation: 'Требуется рассмотрение сотрудником.', demoOnly: true };

describe('versioned application response validation', () => {
  it('accepts absent or null optional details for processing and legacy records', () => {
    expect(isApplication(application)).toBe(true);
    expect(isApplication({ ...application, decisionSummary: null, offer: null })).toBe(true);
  });
  it.each(['MANUAL_REVIEW', 'OFFER_PROPOSED'])('accepts an unresolved %s response with demo terms', (status) => {
    expect(isApplication({ ...application, status, version: 1, offer, decisionSummary })).toBe(true);
  });

  it.each([undefined, null, -1, 1.5, Number.MAX_SAFE_INTEGER + 1, '1'])('rejects an invalid version %s', (version) => {
    expect(isApplication({ ...application, version })).toBe(false);
  });

  it.each([
    { ...offer, annualRate: 'NaN' },
    { ...offer, annualRate: '-1' },
    { ...offer, monthlyPayment: 'Infinity' },
    { ...offer, monthlyPayment: 82724.12 },
    { ...offer, amount: 1500001 },
    { ...offer, amount: 0 },
    { ...offer, termMonths: 0 },
  ])('rejects invalid or excessive offered terms %#', (invalidOffer) => {
    expect(isApplication({ ...application, offer: invalidOffer })).toBe(false);
  });

  it.each([
    { ...decisionSummary, demoOnly: false },
    { ...decisionSummary, assessedAt: 'not-a-date' },
    { ...decisionSummary, ruleVersion: '' },
    { ...decisionSummary, explanation: 42 },
  ])('rejects an invalid decision explanation %#', (invalidSummary) => {
    expect(isApplication({ ...application, decisionSummary: invalidSummary })).toBe(false);
  });
});
