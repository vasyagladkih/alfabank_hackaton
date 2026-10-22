import type { Session } from '../api/types';
import { isSession } from '../api/validation';

export const TOKEN_KEY = 'auth_token';
export const USER_KEY = 'auth_user';

export function clearSession(): void {
  // Storage can be disabled by browser policy. In-memory logout must still work.
  for (const key of [TOKEN_KEY, USER_KEY]) {
    try { localStorage.removeItem(key); } catch { /* Nothing can be persisted in this browser. */ }
  }
}

export function readSession(): Session | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    const rawUser = localStorage.getItem(USER_KEY);
    if (token === null && rawUser === null) return null;
    const candidate: unknown = { token, user: rawUser ? JSON.parse(rawUser) : null };
    if (isSession(candidate)) return candidate;
  } catch { /* Corrupt or inaccessible storage is an unauthenticated session. */ }
  clearSession();
  return null;
}

export function saveSession(session: Session): void {
  if (!isSession(session)) throw new Error('Сервер вернул некорректную сессию. Попробуйте войти ещё раз.');
  try {
    localStorage.setItem(USER_KEY, JSON.stringify(session.user));
    localStorage.setItem(TOKEN_KEY, session.token);
  } catch {
    clearSession();
    throw new Error('Не удалось сохранить сессию. Разрешите хранение данных для этого сайта и повторите вход.');
  }
}
