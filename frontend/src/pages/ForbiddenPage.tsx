import { Link, useLocation } from 'react-router-dom';
import { Button } from '@alfalab/core-components-button';
import { Typography } from '@alfalab/core-components-typography';
import { useAuth, workspacePath } from '../auth/AuthProvider';

export function ForbiddenPage() {
  const { session, logout } = useAuth();
  const location = useLocation();
  if (!session) return null;
  const reason = (location.state as { reason?: string } | null)?.reason;
  const description = reason === 'api'
    ? 'Сервер ограничил доступ к запрошенным данным для вашей роли. Вы можете продолжить работу в своём кабинете.'
    : session.user.role === 'CLIENT'
      ? 'У вашей роли (Клиент) недостаточно прав для просмотра раздела сотрудников банка.'
      : 'Сотрудникам банка закрыт доступ в личный кабинет клиента.';

  return <section className="forbidden-page" aria-labelledby="forbidden-title">
    <div className="forbidden-code" aria-hidden="true">403</div>
    <Typography.TitleResponsive tag="h1" id="forbidden-title" view="medium" font="system" defaultMargins={false}>403 — Доступ ограничен</Typography.TitleResponsive>
    <Typography.Text tag="p" view="primary-large" color="secondary" defaultMargins={false}>{description}</Typography.Text>
    <Typography.Text view="primary-small" color="secondary">Вы остаётесь в системе как {session.user.username}.</Typography.Text>
    <div className="forbidden-actions">
      <Button Component={Link} href={workspacePath(session.user.role)} view="accent" size={48}>Вернуться в свой кабинет</Button>
      <Button view="secondary" size={48} onClick={() => logout()}>Сменить пользователя</Button>
    </div>
  </section>;
}
