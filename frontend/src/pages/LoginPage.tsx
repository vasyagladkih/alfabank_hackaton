import { useEffect, useRef, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Button } from '@alfalab/core-components-button';
import { Typography } from '@alfalab/core-components-typography';
import { Status } from '@alfalab/core-components-status';
import { BriefcaseMIcon } from '@alfalab/icons-glyph/BriefcaseMIcon';
import { BankMIcon } from '@alfalab/icons-glyph/BankMIcon';
import { ArrowRightMIcon } from '@alfalab/icons-glyph/ArrowRightMIcon';
import { useAuth, workspacePath } from '../auth/AuthProvider';
import { errorMessage, isAbortError } from '../api/client';
import type { UserCode } from '../api/types';
import { ErrorNotice } from '../components/Feedback';

const accounts = [
  { code: 'client_good', title: 'Клиент: Надежный заемщик', eyebrow: '01 · Для бизнеса', description: 'Устойчивое финансовое положение. Пройдите путь от заявки до решения банка.', icon: BriefcaseMIcon, badge: 'Надёжный профиль' },
  { code: 'client_bad', title: 'Клиент: Высокий риск', eyebrow: '02 · Для бизнеса', description: 'Повышенная долговая нагрузка. Посмотрите результат скоринга и причину решения.', icon: BriefcaseMIcon, badge: 'Рисковый профиль' },
  { code: 'bank_employee', title: 'Сотрудник банка / Андеррайтер', eyebrow: '03 · Для команды банка', description: 'Все кредитные заявки в одном месте. Следите за статусами и результатами обработки.', icon: BankMIcon, badge: 'Мониторинг заявок' },
] satisfies Array<{ code: UserCode; title: string; eyebrow: string; description: string; icon: typeof BankMIcon; badge: string }>;

export function LoginPage() {
  const { session, login } = useAuth();
  const location = useLocation();
  const [pending, setPending] = useState<UserCode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);

  if (session) return <Navigate to={workspacePath(session.user.role)} replace />;

  async function signIn(code: UserCode) {
    if (request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setPending(code);
    setError(null);
    try { await login(code, controller.signal); }
    catch (cause) { if (!controller.signal.aborted && !isAbortError(cause)) setError(errorMessage(cause)); }
    finally {
      if (!controller.signal.aborted) setPending(null);
      if (request.current === controller) request.current = null;
    }
  }

  return <div className="login-page">
    <section className="login-intro" aria-labelledby="login-title">
      <Typography.Text view="caps" color="primary">Финансирование бизнеса</Typography.Text>
      <Typography.TitleResponsive tag="h1" id="login-title" view="large" font="system" defaultMargins={false}>
        Войдите в кредитный кабинет
      </Typography.TitleResponsive>
      <Typography.Text tag="p" view="primary-large" color="secondary" defaultMargins={false}>
        От заявки до решения — в одном окне. Выберите профиль, чтобы начать работу.
      </Typography.Text>
    </section>
    {Boolean((location.state as { expired?: boolean } | null)?.expired) && <ErrorNotice title="Сессия завершена" message="Войдите снова, чтобы продолжить работу." />}
    {error && <ErrorNotice title="Не удалось войти" message={error} />}
    <section className="login-cards" aria-label="Выбор демо-профиля" aria-busy={pending !== null}>
      {accounts.map(({ code, title, eyebrow, description, icon: Icon, badge }) => <article className="account-card" key={code}>
        <div className="card-topline"><Typography.Text view="secondary-medium" color="secondary">{eyebrow}</Typography.Text><Icon aria-hidden="true" /></div>
        <Typography.Title tag="h2" view="xsmall" font="system" defaultMargins={false}>{title}</Typography.Title>
        <Typography.Text tag="p" view="primary-small" color="secondary" defaultMargins={false}>{description}</Typography.Text>
        <div className="card-badge"><Status color="grey" className="readable-status" uppercase={false}>{badge}</Status></div>
        <Button view={code === 'client_good' ? 'accent' : 'secondary'} block size={48} loading={pending === code} disabled={pending !== null}
          aria-label={`${title}: Войти в кабинет`} onClick={() => void signIn(code)} rightAddons={<ArrowRightMIcon aria-hidden="true" />}>Войти в кабинет</Button>
      </article>)}
    </section>
    <div className="login-caption"><span className="caption-line" /><Typography.Text view="secondary-medium" color="secondary">Три профиля. Один кредитный процесс.</Typography.Text><span className="caption-line" /></div>
  </div>;
}
