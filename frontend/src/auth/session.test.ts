import { describe, expect, it, vi } from 'vitest';
import { clearSession, readSession, saveSession } from './session';
import { clientSession, employeeSession } from '../test/fixtures';

describe('persistent session', () => {
  it.each([clientSession, employeeSession])('restores $user.role after refresh', (session) => {
    saveSession(session);
    expect(localStorage.getItem('auth_token')).toBe(session.token);
    expect(readSession()).toEqual(session);
  });
  it('clears token and profile on logout', () => {
    saveSession(clientSession);
    clearSession();
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
    expect(readSession()).toBeNull();
  });
  it.each([
    [null, JSON.stringify(clientSession.user)],
    ['token', null],
    ['token', '{invalid'],
    ['token', '{}'],
    ['token', JSON.stringify({ ...clientSession.user, role: 'ADMIN' })],
    ['token', JSON.stringify({ ...clientSession.user, id: '101' })],
    ['   ', JSON.stringify(clientSession.user)],
  ])('rejects incomplete or corrupted credentials (%s, %s)', (token, user) => {
    if (token !== null) localStorage.setItem('auth_token', token);
    if (user !== null) localStorage.setItem('auth_user', user);
    expect(readSession()).toBeNull();
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(localStorage.getItem('auth_user')).toBeNull();
  });
  it('does not crash when storage access is denied', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new DOMException('Denied', 'SecurityError'); });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new DOMException('Denied', 'SecurityError'); });
    expect(readSession()).toBeNull();
    expect(() => clearSession()).not.toThrow();
  });
  it('reports a failed persistence instead of pretending login survived a refresh', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Full', 'QuotaExceededError'); });
    expect(() => saveSession(clientSession)).toThrow(/сохранить сессию/i);
  });
});
