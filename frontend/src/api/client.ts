import type { Application, ApplicationDetails, ApplicationInput, ApplicationPage, Session, SignApplicationInput, StatusFilter, UserCode } from './types';
import { isApplication, isApplicationDetails, isApplicationList, isApplicationPage, isSession, normalizeApplication, normalizeApplicationDetails, normalizeApplicationList, normalizeApplicationPage } from './validation';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly code?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Не удалось выполнить запрос. Попробуйте ещё раз.';
}

export interface ApiClientOptions {
  token?: string;
  onAuthError?: (status: 401 | 403) => void;
  baseUrl?: string;
}

type RequestOptions = { method?: 'GET' | 'POST'; body?: unknown; signal?: AbortSignal; public?: boolean; normalize?: (data: unknown) => unknown };

export function createApiClient({ token, onAuthError, baseUrl = import.meta.env.VITE_API_BASE_URL ?? '' }: ApiClientOptions = {}) {
  const origin = baseUrl.replace(/\/+$/, '');

  async function request<T>(path: string, validate: (data: unknown) => data is T, options: RequestOptions = {}): Promise<T> {
    const controller = new AbortController();
    const abort = () => controller.abort();
    options.signal?.addEventListener('abort', abort, { once: true });
    if (options.signal?.aborted) controller.abort();
    const timeout = setTimeout(abort, 15_000);
    const headers = new Headers({ Accept: 'application/json' });
    if (options.body !== undefined) headers.set('Content-Type', 'application/json');
    if (!options.public && token) headers.set('Authorization', `Bearer ${token}`);
    try {
      controller.signal.throwIfAborted();
      const response = await fetch(`${origin}${path}`, {
        method: options.method ?? 'GET', headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal,
        cache: 'no-store',
      });
      controller.signal.throwIfAborted();
      const data: unknown = await response.json().catch(() => null);
      controller.signal.throwIfAborted();
      if (!response.ok) {
        if (!options.public && (response.status === 401 || response.status === 403)) onAuthError?.(response.status);
        const message = data && typeof data === 'object' && 'message' in data && typeof data.message === 'string' ? data.message : `Сервер вернул ошибку ${response.status}. Попробуйте ещё раз.`;
        const code = data && typeof data === 'object' && 'code' in data && typeof data.code === 'string' ? data.code : undefined;
        throw new ApiError(response.status, message, code);
      }
      const normalized = options.normalize ? options.normalize(data) : data;
      if (!validate(normalized)) throw new ApiError(502, 'Ответ сервера не соответствует формату API. Повторите запрос позже.', 'INVALID_RESPONSE');
      return normalized;
    } catch (error) {
      if (options.signal?.aborted) throw new DOMException('Request cancelled', 'AbortError');
      if (controller.signal.aborted) throw new ApiError(0, 'Сервер не ответил вовремя. Проверьте соединение и повторите запрос.');
      if (error instanceof ApiError) throw error;
      throw new ApiError(0, 'Не удалось связаться с сервером. Проверьте соединение и повторите запрос.');
    } finally {
      clearTimeout(timeout);
      options.signal?.removeEventListener('abort', abort);
    }
  }

  return {
    demoLogin: (userCode: UserCode, signal?: AbortSignal): Promise<Session> =>
      request('/api/v1/auth/demo-login', isSession, { method: 'POST', body: { userCode }, signal, public: true }),
    createApplication: (input: ApplicationInput, signal?: AbortSignal): Promise<Application> =>
      request('/api/v1/client/applications', isApplication, { method: 'POST', body: input, signal, normalize: normalizeApplication }),
    getClientApplications: (signal?: AbortSignal): Promise<Application[]> =>
      request('/api/v1/client/applications', isApplicationList, { signal, normalize: normalizeApplicationList }),
    getApplication: (id: number, signal?: AbortSignal): Promise<Application> =>
      request(`/api/v1/client/applications/${encodeURIComponent(id)}`, isApplication, { signal, normalize: normalizeApplication }),
    signApplication: (id: number, input: SignApplicationInput, signal?: AbortSignal): Promise<Application> =>
      request(`/api/v1/client/applications/${encodeURIComponent(id)}/sign`, isApplication, { method: 'POST', body: input, signal, normalize: normalizeApplication }),
    getEmployeeApplicationDetails: (id: number, signal?: AbortSignal): Promise<ApplicationDetails> =>
      request(`/api/v1/employee/applications/${encodeURIComponent(id)}`, isApplicationDetails, { signal, normalize: normalizeApplicationDetails }),
    retryApplication: (id: number, signal?: AbortSignal): Promise<Application> =>
      request(`/api/v1/employee/applications/${encodeURIComponent(id)}/retry`, isApplication, { method: 'POST', signal, normalize: normalizeApplication }),
    getEmployeeApplications(status: StatusFilter, page: number, size: number, signal?: AbortSignal): Promise<ApplicationPage> {
      const query = new URLSearchParams({ page: String(page), size: String(size) });
      if (status !== 'ALL') query.set('status', status);
      return request(`/api/v1/employee/applications?${query}`, isApplicationPage, { signal, normalize: normalizeApplicationPage });
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
