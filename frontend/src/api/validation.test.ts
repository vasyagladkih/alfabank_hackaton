import { describe, expect, it } from 'vitest';
import { application, applicationPage } from '../test/fixtures';
import { isApplication, isApplicationPage } from './validation';

describe('application response validation', () => {
  it.each(['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED', 'REJECTED'])('accepts the %s status', (status) => {
    expect(isApplication({ ...application, status })).toBe(true);
  });

  it.each([{ status: 'UNKNOWN' }, { status: null }, { status: ['SIGNED'] }])('rejects invalid status %j', ({ status }) => {
    expect(isApplication({ ...application, status })).toBe(false);
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
