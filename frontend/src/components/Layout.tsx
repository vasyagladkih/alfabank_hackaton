import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@alfalab/core-components-button';
import { Typography } from '@alfalab/core-components-typography';
import { Status } from '@alfalab/core-components-status';
import { LogOutMIcon } from '@alfalab/icons-glyph/LogOutMIcon';
import { useAuth, workspacePath } from '../auth/AuthProvider';

export function Layout({ children }: { children: ReactNode }) {
  const { session, logout } = useAuth();
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Перейти к содержимому</a>
    <header className="site-header">
      <div className="header-inner">
        <Link to={session ? workspacePath(session.user.role) : '/login'} className="brand">
          <span className="brand-wordmark">Альфа-Банк</span>
          <span className="brand-product">Кредитный кабинет</span>
        </Link>
        <div className="header-actions">
          <Status color="grey" view="muted-alt" className="readable-status" uppercase={false}>Демо</Status>
          {session && <Button view="text" size={40} leftAddons={<LogOutMIcon aria-hidden="true" />} onClick={() => logout()}>Выйти</Button>}
        </div>
      </div>
    </header>
    <main id="main-content" className="main-content" tabIndex={-1}>{children}</main>
    <footer className="site-footer">
      <Typography.Text view="secondary-medium" color="secondary">Альфа-Банк · Малый и микробизнес</Typography.Text>
      <Typography.Text view="secondary-small" color="secondary">Демонстрационный кредитный кабинет</Typography.Text>
      <a className="license-link" href="/third-party-notices.txt">Лицензии компонентов</a>
    </footer>
  </div>;
}

export function IdentityBar() {
  const { session } = useAuth();
  if (!session) return null;
  const { user } = session;
  return <div className="identity-bar">
    <div className="avatar" aria-hidden="true">{(user.companyName || user.username).slice(0, 1).toUpperCase()}</div>
    <div className="identity-copy">
      <Typography.Text weight="medium">{user.role === 'CLIENT' ? user.companyName || user.username : user.username}</Typography.Text>
      <Typography.Text view="secondary-medium" color="secondary">
        {user.role === 'CLIENT' ? `ИНН ${user.inn || 'не указан'}` : 'Сотрудник банка · Андеррайтер'}
      </Typography.Text>
    </div>
    <span className="identity-section">{user.role === 'CLIENT' ? 'Личный кабинет' : 'Бэк-офис'}</span>
  </div>;
}
