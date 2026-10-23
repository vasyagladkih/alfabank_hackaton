import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from './client';
import { employeeDossier } from '../test/dossier';
import { jsonResponse } from '../test/fixtures';

describe('restricted dossier response boundary', () => {
  it('loads a validated employee dossier through its authorized endpoint', async () => {
    const requests: Array<{ url: string; authorization: string | null }> = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      requests.push({ url, authorization: new Headers(init.headers).get('Authorization') });
      return jsonResponse(employeeDossier);
    }));
    expect(await createApiClient({ token: 'employee-token' }).getEmployeeDossier(42)).toEqual(employeeDossier);
    expect(requests).toEqual([{ url: '/api/v1/employee/applications/42', authorization: 'Bearer employee-token' }]);
  });

  it.each([
    { ...employeeDossier, demoOnly: false },
    { ...employeeDossier, sources: [{ ...employeeDossier.sources[0], freshness: 'LIVE' }] },
    { ...employeeDossier, sources: [{ ...employeeDossier.sources[0], observedOn: 'yesterday' }] },
    { ...employeeDossier, assessment: { ...employeeDossier.assessment, debtRatio: 'NaN' } },
    { ...employeeDossier, assessment: { ...employeeDossier.assessment, score: 851 } },
    { ...employeeDossier, allowedActions: ['DISBURSE'] },
    { ...employeeDossier, allowedActions: [['APPROVE']] },
    { ...employeeDossier, audit: [{ ...employeeDossier.audit[0], actorRole: 'ADMIN' }] },
    { ...employeeDossier, cashflows: [{ month: '2026-13', turnover: '100.00' }] },
  ])('rejects unsafe or malformed nested dossier data %#', async (body) => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(body)));
    await expect(createApiClient({ token: 'employee-token' }).getEmployeeDossier(42)).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' });
  });

  it('accepts explicit missing evidence and signed raw values without inventing a score', async () => {
    const dossier = { ...employeeDossier, assessment: null, cashflows: [{ month: '2026-09', turnover: '-100.00' }], allowedActions: ['REJECT'], snapshotCapturedAt: null };
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(dossier)));
    expect(await createApiClient({ token: 'employee-token' }).getEmployeeDossier(42)).toEqual(dossier);
  });
});
