import { useEffect } from 'react';
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth, workspacePath } from './auth/AuthProvider';
import type { UserRole } from './api/types';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { ClientPage } from './pages/ClientPage';
import { EmployeePage } from './pages/EmployeePage';

function RequireRole({ role }: { role?: UserRole }) {
  const { session } = useAuth();
  const location = useLocation();
  if (!session) return <Navigate to="/login" replace />;
  if (role && session.user.role !== role) return <Navigate to="/403" replace state={{ reason: 'role', from: location.pathname }} />;
  return <Outlet key={`${session.user.id}:${session.token}`} />;
}

function FallbackRoute() {
  const { session } = useAuth();
  return <Navigate to={session ? workspacePath(session.user.role) : '/login'} replace />;
}

function RouteFocus() {
  const { pathname } = useLocation();
  useEffect(() => {
    const heading = document.querySelector('main h1');
    if (heading instanceof HTMLElement) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
      document.title = `${heading.textContent} · Альфа-Банк`;
    }
  }, [pathname]);
  return null;
}

export function App() {
  return <AuthProvider><Layout><Routes>
    <Route path="/" element={<Navigate to="/login" replace />} />
    <Route path="/login" element={<LoginPage />} />
    <Route element={<RequireRole role="CLIENT" />}><Route path="/client" element={<ClientPage />} /></Route>
    <Route element={<RequireRole role="EMPLOYEE" />}><Route path="/employee" element={<EmployeePage />} /></Route>
    <Route element={<RequireRole />}><Route path="/403" element={<ForbiddenPage />} /></Route>
    <Route path="*" element={<FallbackRoute />} />
  </Routes><RouteFocus /></Layout></AuthProvider>;
}
