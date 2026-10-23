import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { App } from './App';
import { application, clientSession, deferred, employeeSession, jsonResponse, storeSession } from './test/fixtures';

function NavigationProbe() {
  const navigate = useNavigate();
  return <button onClick={() => navigate('/employee')}>Тест: открыть бэк-офис</button>;
}

function renderApp(path = '/login') {
  return render(<MemoryRouter initialEntries={[path]}><App /><NavigationProbe /></MemoryRouter>);
}

describe('role routes and session lifecycle', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn(async () => jsonResponse([]))));

  it.each(['/client', '/employee'])('redirects guest %s to login', async (path) => {
    renderApp(path);
    expect(await screen.findByRole('heading', { name: 'Войдите в кредитный кабинет' })).toBeVisible();
    expect(fetch).not.toHaveBeenCalled();
  });

  it.each([
    ['CLIENT', '/employee', 'Кредит для вашего бизнеса'],
    ['EMPLOYEE', '/client', 'Заявки на финансирование'],
  ] as const)('keeps %s session on a forbidden route and returns to its workspace', async (role, path, ownHeading) => {
    const session = storeSession(role);
    renderApp(path);
    expect(await screen.findByRole('heading', { name: '403 — Доступ ограничен' })).toBeVisible();
    expect(localStorage.getItem('auth_token')).toBe(session.token);
    expect(fetch).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('link', { name: 'Вернуться в свой кабинет' }));
    expect(await screen.findByRole('heading', { name: ownHeading })).toBeVisible();
  });

  it.each([
    ['CLIENT', '/client', 'Кредит для вашего бизнеса'],
    ['EMPLOYEE', '/employee', 'Заявки на финансирование'],
  ] as const)('restores %s from storage on refresh', async (role, path, heading) => {
    const session = storeSession(role);
    renderApp(path);
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible();
    expect(localStorage.getItem('auth_token')).toBe(session.token);
    await waitFor(() => expect(fetch).toHaveBeenCalled());
  });

  it.each(['APPROVED', 'REJECTED'] as const)('keeps %s banners when restored pending applications finish', async (status) => {
    storeSession();
    const pending = [application, { ...application, id: 43 }];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const item = pending.find(({ id }) => url.endsWith(`/applications/${id}`));
      return item
        ? jsonResponse({ ...item, status, version: 1, rejectionReason: status === 'REJECTED' ? 'Высокая долговая нагрузка' : null })
        : jsonResponse([...pending, { ...application, id: 41, status: 'APPROVED' }]);
    }));
    renderApp('/client');
    await waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(2));
    expect(screen.getByRole('link', { name: 'Текущие заявки (2)' })).toHaveAttribute('href', '#current-applications');
    expect(screen.getByText('Заявка №42 в обработке', { exact: true })).toBeVisible();
    expect(screen.getByText('Заявка №43 в обработке', { exact: true })).toBeVisible();
    await waitFor(() => {
      const banners = screen.getAllByRole('alert');
      expect(banners).toHaveLength(2);
      for (const banner of banners) {
        expect(banner).toHaveTextContent(status === 'APPROVED' ? 'Одобрено' : 'Отклонено');
        if (status === 'REJECTED') expect(banner).toHaveTextContent('Высокая долговая нагрузка');
      }
    }, { timeout: 4000 });
    expect(screen.getByRole('link', { name: 'Текущие заявки (2)' })).toBeVisible();
  });

  it.each(['MANUAL_REVIEW', 'OFFER_PROPOSED'] as const)('keeps the banner when a restored %s application finishes', async (status) => {
    storeSession();
    vi.stubGlobal('fetch', vi.fn(async (url: string) => jsonResponse(url.endsWith('/42')
      ? { ...application, status: 'APPROVED', version: 3 }
      : [{ ...application, status, version: 1 }])));
    renderApp('/client');
    await waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(1));
    expect(screen.getByRole('link', { name: 'Текущие заявки (1)' })).toBeVisible();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Одобрено'), { timeout: 4000 });
    expect(screen.getByRole('link', { name: 'Текущие заявки (1)' })).toBeVisible();
  });

  it.each([
    ['Клиент: Надежный заемщик', 'client_good', clientSession, 'Кредит для вашего бизнеса'],
    ['Клиент: Высокий риск', 'client_bad', { ...clientSession, user: { ...clientSession.user, username: 'client_bad' } }, 'Кредит для вашего бизнеса'],
    ['Сотрудник банка / Андеррайтер', 'bank_employee', employeeSession, 'Заявки на финансирование'],
  ] as const)('logs in with %s', async (label, code, session, heading) => {
    const calls: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (url.endsWith('/demo-login')) {
        calls.push(String(init.body));
        return jsonResponse(session);
      }
      return jsonResponse([]);
    }));
    renderApp();
    fireEvent.click(screen.getByRole('button', { name: `${label}: Войти в кабинет` }));
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible();
    expect(calls).toEqual([JSON.stringify({ userCode: code })]);
    expect(localStorage.getItem('auth_token')).toBe(session.token);
    expect(JSON.parse(localStorage.getItem('auth_user')!)).toEqual(session.user);
  });

  it('clears both credentials on logout', async () => {
    storeSession();
    renderApp('/client');
    fireEvent.click(await screen.findByRole('button', { name: 'Выйти' }));
    expect(await screen.findByRole('heading', { name: 'Войдите в кредитный кабинет' })).toBeVisible();
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
  });

  it('switch user from 403 clears both credentials', async () => {
    storeSession();
    renderApp('/employee');
    fireEvent.click(await screen.findByRole('button', { name: 'Сменить пользователя' }));
    expect(await screen.findByRole('heading', { name: 'Войдите в кредитный кабинет' })).toBeVisible();
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
  });

  it.each([401, 403])('handles API %s without confusing expiration and authorization', async (status) => {
    storeSession();
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ message: 'Нет доступа' }, status)));
    renderApp('/client');
    const heading = status === 401 ? 'Войдите в кредитный кабинет' : '403 — Доступ ограничен';
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible();
    expect(localStorage.getItem('auth_token')).toBe(status === 401 ? null : clientSession.token);
  });

  it('updates routes when another tab changes the session', async () => {
    storeSession();
    renderApp('/client');
    await screen.findByRole('heading', { name: 'Кредит для вашего бизнеса' });
    act(() => {
      storeSession('EMPLOYEE');
      window.dispatchEvent(new StorageEvent('storage', { key: 'auth_token' }));
    });
    expect(await screen.findByRole('heading', { name: '403 — Доступ ограничен' })).toBeVisible();
    expect(localStorage.getItem('auth_token')).toBe(employeeSession.token);
  });

  it('does not let an old response expire a newly selected session', async () => {
    storeSession();
    const pending = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn((url: string) => url.includes('/client/') ? pending.promise : Promise.resolve(jsonResponse([]))));
    renderApp('/client');
    await waitFor(() => expect(fetch).toHaveBeenCalled());
    act(() => {
      storeSession('EMPLOYEE');
      window.dispatchEvent(new StorageEvent('storage', { key: 'auth_token' }));
    });
    fireEvent.click(await screen.findByRole('link', { name: 'Вернуться в свой кабинет' }));
    await act(async () => pending.resolve(jsonResponse({}, 401)));
    expect(await screen.findByRole('heading', { name: 'Заявки на финансирование' })).toBeVisible();
    expect(localStorage.getItem('auth_token')).toBe(employeeSession.token);
  });
});
