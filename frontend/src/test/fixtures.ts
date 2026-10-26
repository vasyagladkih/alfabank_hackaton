import type { Application, ApplicationPage, Session, UserRole } from '../api/types';

export const clientSession: Session = {
  token: 'test-client-token',
  user: { id: 101, username: 'client_good', role: 'CLIENT', companyName: 'ООО «Ромашка»', inn: '7701234567' },
};
export const employeeSession: Session = {
  token: 'test-employee-token',
  user: { id: 201, username: 'Анна Смирнова', role: 'EMPLOYEE' },
};
export const application: Application = {
  id: 42,
  companyName: 'ООО «Ромашка»',
  inn: '7701234567',
  amount: 1500000,
  termMonths: 12,
  purpose: 'WORKING_CAPITAL',
  status: 'PROCESSING',
  rejectionReason: null,
  createdAt: '2026-10-22T14:30:00Z',
  updatedAt: '2026-10-22T14:30:00Z',
};

export const applicationPage: ApplicationPage = {
  items: [application], page: 0, size: 10, totalElements: 1, totalPages: 1,
};

export function storeSession(role: UserRole = 'CLIENT') {
  const session = role === 'CLIENT' ? clientSession : employeeSession;
  localStorage.setItem('auth_token', session.token);
  localStorage.setItem('auth_user', JSON.stringify(session.user));
  return session;
}

export function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
