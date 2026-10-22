import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './client';
import { application, clientSession, deferred, jsonResponse } from '../test/fixtures';

describe('HTTP API contract', () => {
  it.each(['client_good', 'client_bad', 'bank_employee'] as const)('posts %s without Authorization', async (userCode) => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(clientSession));
    vi.stubGlobal('fetch', fetch);
    await createApiClient({ token: 'previous-session' }).demoLogin(userCode);
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe('/api/v1/auth/demo-login');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ userCode });
    expect(new Headers(init.headers).has('Authorization')).toBe(false);
  });
  it('sends exactly the application payload and Bearer token', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(application, 201));
    vi.stubGlobal('fetch', fetch);
    const input = { amount: 100000, termMonths: 1, purpose: 'EQUIPMENT' as const };
    expect(await createApiClient({ token: 'test-token' }).createApplication(input)).toEqual(application);
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe('/api/v1/client/applications');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual(input);
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer test-token');
  });
  it.each([401, 403] as const)('reports protected HTTP %s without conflating statuses', async (status) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ code: 'ACCESS_ERROR', message: 'Нет доступа', timestamp: '2026-10-22T14:30:00Z' }, status)));
    const onAuthError = vi.fn();
    await expect(createApiClient({ token: 'test-token', onAuthError }).getClientApplications()).rejects.toMatchObject({ status, message: 'Нет доступа' });
    expect(onAuthError).toHaveBeenCalledExactlyOnceWith(status);
  });
  it('does not expire a session on a demo-login error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ message: 'Неверный код' }, 401)));
    const onAuthError = vi.fn();
    await expect(createApiClient({ onAuthError }).demoLogin('client_good')).rejects.toBeInstanceOf(ApiError);
    expect(onAuthError).not.toHaveBeenCalled();
  });
  it('ALL fetches each explicit status, merges duplicate transitions and sorts newest first', async () => {
    const fetch = vi.fn(async (url: string) => {
      if (url.endsWith('status=PROCESSING')) return jsonResponse([application]);
      if (url.endsWith('status=APPROVED')) return jsonResponse([{ ...application, status: 'APPROVED', updatedAt: '2026-10-22T14:31:00Z' }]);
      if (url.endsWith('status=REJECTED')) return jsonResponse([{ ...application, id: 43, status: 'REJECTED', createdAt: '2026-10-22T14:32:00Z' }]);
      throw new Error(`Unexpected unfiltered request: ${url}`);
    });
    vi.stubGlobal('fetch', fetch);
    const result = await createApiClient({ token: 'token' }).getEmployeeApplications('ALL');
    expect(result.map(({ id, status }) => ({ id, status }))).toEqual([{ id: 43, status: 'REJECTED' }, { id: 42, status: 'APPROVED' }]);
    expect(fetch).toHaveBeenCalledTimes(3);
  });
  it.each(['PROCESSING', 'APPROVED', 'REJECTED'] as const)('passes the %s filter explicitly', async (status) => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetch);
    await createApiClient({ token: 'token' }).getEmployeeApplications(status);
    expect(fetch.mock.calls[0]![0]).toBe(`/api/v1/employee/applications?status=${status}`);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('fails ALL instead of presenting an incomplete registry', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => url.endsWith('status=APPROVED') ? jsonResponse({ message: 'Unavailable' }, 503) : jsonResponse([application])));
    await expect(createApiClient({ token: 'token' }).getEmployeeApplications('ALL')).rejects.toMatchObject({ status: 503 });
  });
  it('ignores a late auth error after cancellation', async () => {
    const response = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(() => response.promise));
    const onAuthError = vi.fn();
    const controller = new AbortController();
    const result = createApiClient({ token: 'old', onAuthError }).getClientApplications(controller.signal);
    controller.abort();
    response.resolve(jsonResponse({}, 401));
    await expect(result).rejects.toMatchObject({ name: 'AbortError' });
    expect(onAuthError).not.toHaveBeenCalled();
  });
  it('keeps a useful error for a non-JSON gateway response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 })));
    await expect(createApiClient({ token: 'token' }).getClientApplications()).rejects.toMatchObject({ status: 502 });
  });
});
