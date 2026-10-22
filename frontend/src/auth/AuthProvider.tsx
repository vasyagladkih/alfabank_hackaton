import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { createApiClient } from '../api/client';
import type { ApiClient } from '../api/client';
import type { Session, UserCode, UserRole } from '../api/types';
import { clearSession, readSession, saveSession, TOKEN_KEY, USER_KEY } from './session';

interface AuthValue {
  session: Session | null;
  api: ApiClient;
  login: (code: UserCode, signal: AbortSignal) => Promise<void>;
  logout: (expired?: boolean) => void;
}

export const workspacePath = (role: UserRole) => role === 'CLIENT' ? '/client' : '/employee';
const AuthContext = createContext<AuthValue | null>(null);
const publicApi = createApiClient();

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState(readSession);
  const current = useRef(session);
  const generation = useRef(0);
  const navigate = useNavigate();

  const applySession = useCallback((next: Session | null) => {
    generation.current += 1;
    current.current = next;
    setSession(next);
  }, []);

  const logout = useCallback((expired = false) => {
    clearSession();
    applySession(null);
    navigate('/login', { replace: true, state: { expired } });
  }, [applySession, navigate]);

  const login = useCallback(async (code: UserCode, signal: AbortSignal) => {
    const startedAt = generation.current;
    const next = await publicApi.demoLogin(code, signal);
    if (signal.aborted || generation.current !== startedAt) throw new DOMException('Login cancelled', 'AbortError');
    saveSession(next);
    applySession(next);
    navigate(workspacePath(next.user.role), { replace: true });
  }, [applySession, navigate]);

  const api = useMemo(() => createApiClient({
    token: session?.token,
    onAuthError: (status) => {
      // The response belongs to this particular session, not just this username.
      if (!session || current.current !== session) return;
      if (status === 401) logout(true);
      else navigate('/403', { replace: true, state: { reason: 'api' } });
    },
  }), [session, logout, navigate]);

  useEffect(() => {
    const synchronize = (event: StorageEvent) => {
      if (event.key !== null && event.key !== TOKEN_KEY && event.key !== USER_KEY) return;
      const next = readSession();
      if (next?.token === current.current?.token && JSON.stringify(next?.user) === JSON.stringify(current.current?.user)) return;
      applySession(next);
    };
    window.addEventListener('storage', synchronize);
    return () => window.removeEventListener('storage', synchronize);
  }, [applySession]);

  useEffect(() => () => { generation.current += 1; }, []);

  return <AuthContext.Provider value={{ session, api, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('AuthProvider is required');
  return value;
}
