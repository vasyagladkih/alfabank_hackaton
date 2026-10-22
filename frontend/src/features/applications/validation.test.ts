import { describe, expect, it } from 'vitest';
import { validateApplication } from './validation';

describe('application validation', () => {
  const valid = { amount: '1500000', termMonths: '12', purpose: 'WORKING_CAPITAL' };
  it.each(['100000', '50000000'])('accepts inclusive amount boundary %s', (amount) => {
    expect(validateApplication({ ...valid, amount })).toEqual({});
  });
  it.each(['1', '60'])('accepts inclusive term boundary %s', (termMonths) => {
    expect(validateApplication({ ...valid, termMonths })).toEqual({});
  });
  it.each(['WORKING_CAPITAL', 'EQUIPMENT', 'BUSINESS_EXPANSION', 'REFINANCING'])('accepts contract purpose %s', (purpose) => {
    expect(validateApplication({ ...valid, purpose })).toEqual({});
  });
  it.each(['', ' ', '99999', '50000001', '-1', '100000.5', 'Infinity', 'NaN', 'abc'])('rejects invalid amount %s', (amount) => {
    expect(validateApplication({ ...valid, amount }).amount).toBeTruthy();
  });
  it.each(['', '0', '61', '1.5', '-1', 'Infinity', 'abc'])('rejects invalid term %s', (termMonths) => {
    expect(validateApplication({ ...valid, termMonths }).termMonths).toBeTruthy();
  });
  it.each(['', 'UNKNOWN', '__proto__', 'toString'])('rejects invalid purpose %s', (purpose) => {
    expect(validateApplication({ ...valid, purpose }).purpose).toBeTruthy();
  });
});
