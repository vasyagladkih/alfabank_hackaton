import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './client';
import { application, applicationDetails, applicationPage, approvedApplication, clientSession, deferred, jsonResponse, paymentSchedule } from '../test/fixtures';

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
  it('signs with the unchanged consent and confirmation code payload', async () => {
    const signed = { ...approvedApplication, status: 'SIGNED' };
    const fetch = vi.fn().mockResolvedValue(jsonResponse(signed));
    vi.stubGlobal('fetch', fetch);
    const input = { confirmTerms: true, smsCode: 'confirmation-code' };
    expect(await createApiClient({ token: 'test-token' }).signApplication(42, input)).toEqual(signed);
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe('/api/v1/client/applications/42/sign');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual(input);
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json');
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer test-token');
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('loads the employee dossier through the protected detail endpoint', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(applicationDetails));
    vi.stubGlobal('fetch', fetch);
    expect(await createApiClient({ token: 'employee-token' }).getEmployeeApplicationDetails(42)).toEqual(applicationDetails);
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe('/api/v1/employee/applications/42');
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer employee-token');
  });
  it.each(['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'REJECTED'] as const)('retries without a body and preserves the synchronous %s result', async (status) => {
    const updated = { ...application, status, schedule: null };
    const fetch = vi.fn().mockResolvedValue(jsonResponse(updated));
    vi.stubGlobal('fetch', fetch);
    expect(await createApiClient({ token: 'employee-token' }).retryApplication(42)).toEqual({ ...application, status });
    const [url, init] = fetch.mock.calls[0]!;
    expect(url).toBe('/api/v1/employee/applications/42/retry');
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
    expect(new Headers(init.headers).has('Content-Type')).toBe(false);
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer employee-token');
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it.each(['sign', 'retry'] as const)('does not repeat a failed %s POST', async (operation) => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse({ code: 'INVALID_STATE', message: 'Статус заявки изменился' }, 409));
    vi.stubGlobal('fetch', fetch);
    const api = createApiClient();
    const result = operation === 'sign' ? api.signApplication(42, { confirmTerms: true, smsCode: '1234' }) : api.retryApplication(42);
    await expect(result).rejects.toMatchObject({ status: 409, code: 'INVALID_STATE', message: 'Статус заявки изменился' });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('normalizes absent Java schedules in application, history and employee page responses', async () => {
    const withoutSchedule = { ...application, schedule: null };
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(jsonResponse(withoutSchedule))
      .mockResolvedValueOnce(jsonResponse([withoutSchedule]))
      .mockResolvedValueOnce(jsonResponse({ ...applicationPage, items: [withoutSchedule] })));
    const api = createApiClient();
    expect(await api.getApplication(42)).toEqual(application);
    expect(await api.getClientApplications()).toEqual([application]);
    expect(await api.getEmployeeApplications('ALL', 0, 10)).toEqual(applicationPage);
  });
  it('normalizes optional dossier nulls before strict validation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      ...application, reviewReasonCode: null, canRetry: false, user: null, fns: null, bki: null,
      scoring: { scoringScore: null, calculatedPdnPercent: 0, maxLimitRub: null }, schedule: null,
      approvedAmount: null, approvedRate: null,
    })));
    expect(await createApiClient().getEmployeeApplicationDetails(42)).toEqual({
      ...application, canRetry: false, scoring: { calculatedPdnPercent: 0 }, approvedAmount: null, approvedRate: null,
    });
  });
  it.each([
    { schedule: { ...paymentSchedule, items: [{ ...paymentSchedule.items[0], paymentDate: '2026-02-30' }] } },
    { bki: { monthlyPaymentRub: '0' } }, { scoring: { maxLimitRub: 1.5 } }, { canRetry: 'true' },
  ])('rejects malformed nested dossier fields %#', async (invalid) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...applicationDetails, ...invalid })));
    await expect(createApiClient().getEmployeeApplicationDetails(42)).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' });
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
  it('loads ALL with one paged request, omits status and preserves the server response', async () => {
    const page = { ...applicationPage, items: [application, { ...application, id: 43, status: 'SIGNED', createdAt: '2026-10-22T14:32:00Z' }], page: 2, totalElements: 32, totalPages: 4 };
    const fetch = vi.fn().mockResolvedValue(jsonResponse(page));
    vi.stubGlobal('fetch', fetch);
    expect(await createApiClient({ token: 'token' }).getEmployeeApplications('ALL', 2, 10)).toEqual(page);
    expect(fetch.mock.calls[0]![0]).toBe('/api/v1/employee/applications?page=2&size=10');
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it.each(['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED', 'REJECTED'] as const)('passes the %s filter explicitly with pagination', async (status) => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(applicationPage));
    vi.stubGlobal('fetch', fetch);
    await createApiClient({ token: 'token' }).getEmployeeApplications(status, 1, 20);
    expect(fetch.mock.calls[0]![0]).toBe(`/api/v1/employee/applications?page=1&size=20&status=${status}`);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it.each([
    { body: [] },
    { body: { ...applicationPage, totalPages: undefined } },
    { body: { ...applicationPage, page: -1 } },
    { body: { ...applicationPage, items: [{ ...application, status: 'UNKNOWN' }] } },
  ])('rejects an invalid employee page response %#', async ({ body }) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(body)));
    await expect(createApiClient({ token: 'token' }).getEmployeeApplications('ALL', 0, 10)).rejects.toMatchObject({ status: 502, code: 'INVALID_RESPONSE' });
  });
  it('accepts an empty registry page', async () => {
    const empty = { ...applicationPage, items: [], totalElements: 0, totalPages: 0 };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(empty)));
    expect(await createApiClient().getEmployeeApplications('ALL', 0, 10)).toEqual(empty);
  });
  it('cancels an employee page request and ignores its late response', async () => {
    const response = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>(() => response.promise);
    vi.stubGlobal('fetch', fetch);
    const controller = new AbortController();
    const result = createApiClient().getEmployeeApplications('ALL', 0, 10, controller.signal);
    const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
    controller.abort();
    expect(signal.aborted).toBe(true);
    response.resolve(jsonResponse(applicationPage));
    await expect(result).rejects.toMatchObject({ name: 'AbortError' });
  });
  it.each(['sign', 'details', 'retry'] as const)('cancels %s and ignores a late protected response', async (operation) => {
    const response = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>(() => response.promise);
    vi.stubGlobal('fetch', fetch);
    const controller = new AbortController();
    const onAuthError = vi.fn();
    const api = createApiClient({ token: 'old-token', onAuthError });
    const result = operation === 'sign'
      ? api.signApplication(42, { confirmTerms: true, smsCode: '1234' }, controller.signal)
      : operation === 'details' ? api.getEmployeeApplicationDetails(42, controller.signal) : api.retryApplication(42, controller.signal);
    const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
    controller.abort();
    expect(signal.aborted).toBe(true);
    response.resolve(jsonResponse({ message: 'Сессия завершена' }, 401));
    await expect(result).rejects.toMatchObject({ name: 'AbortError' });
    expect(onAuthError).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledTimes(1);
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
