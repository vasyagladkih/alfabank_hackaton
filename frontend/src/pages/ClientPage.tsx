import { useEffect, useState } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Typography } from '@alfalab/core-components-typography';
import { useAuth } from '../auth/AuthProvider';
import { IdentityBar } from '../components/Layout';
import { EmptyState, ErrorNotice, Loading } from '../components/Feedback';
import { ApplicationForm } from '../features/applications/ApplicationForm';
import { ApplicationResult } from '../features/applications/ApplicationResult';
import { ApplicationsTable } from '../features/applications/ApplicationsTable';
import { useClientApplications } from '../features/applications/useClientApplications';

export function ClientPage() {
  const { api } = useAuth();
  const { applications, loading, error, pollIssues, refresh, update, retryPolling } = useClientApplications(api);
  const [trackedIds, setTrackedIds] = useState<Set<number>>(() => new Set());
  useEffect(() => {
    setTrackedIds((current) => {
      const pending = applications.filter((item) => item.status === 'PROCESSING' && !current.has(item.id));
      return pending.length > 0 ? new Set([...current, ...pending.map((item) => item.id)]) : current;
    });
  }, [applications]);
  const active = applications.filter((item) => item.status === 'PROCESSING' || trackedIds.has(item.id));

  return <>
    <IdentityBar />
    <div className="page-heading">
      <Typography.Text view="caps" color="secondary">Возможности для роста</Typography.Text>
      <Typography.TitleResponsive tag="h1" view="medium" font="system" defaultMargins={false}>Кредит для вашего бизнеса</Typography.TitleResponsive>
      <Typography.Text tag="p" view="primary-medium" color="secondary" defaultMargins={false}>Подайте заявку и следите за решением в личном кабинете.</Typography.Text>
    </div>
    <div className="result-list">{active.map((application) => <ApplicationResult key={application.id} application={application} issue={pollIssues[application.id]} retry={() => retryPolling(application.id)} />)}</div>
    <div className="client-grid">
      <section className="panel form-panel">
        <ApplicationForm onCreated={(application) => {
          update(application);
          setTrackedIds((current) => new Set([...current, application.id]));
        }} />
      </section>
      <aside className="credit-aside" aria-label="Условия и этапы заявки">
        <div className="credit-limit">
          <Typography.Text view="primary-small" color="secondary">На задачи вашего бизнеса</Typography.Text>
          <Typography.Title tag="div" view="large" font="system" defaultMargins={false}>до 50 млн ₽</Typography.Title>
          <Typography.Text view="primary-medium">на срок до 60 месяцев</Typography.Text>
        </div>
        <div className="process-steps">
          <Typography.Title tag="h2" view="xsmall" font="system" defaultMargins={false}>Как это работает</Typography.Title>
          <ol>
            <li><span className="step-number" aria-hidden="true">1</span><div><strong>Параметры кредита</strong><p>Выберите сумму, срок и цель.</p></div></li>
            <li><span className="step-number" aria-hidden="true">2</span><div><strong>Обработка заявки</strong><p>Банк проверит данные бизнеса.</p></div></li>
            <li><span className="step-number" aria-hidden="true">3</span><div><strong>Решение в кабинете</strong><p>Статус обновится автоматически.</p></div></li>
          </ol>
        </div>
      </aside>
    </div>
    <section className="panel history-panel" aria-labelledby="history-title">
      <div className="section-toolbar">
        <div className="section-heading"><Typography.Title tag="h2" id="history-title" view="small" font="system" defaultMargins={false}>История заявок</Typography.Title>
          <Typography.Text view="primary-small" color="secondary">Ваши заявки и результаты рассмотрения</Typography.Text></div>
        <Button view="secondary" size={40} onClick={refresh} disabled={loading} loading={loading}>Обновить историю</Button>
      </div>
      {error && <ErrorNotice title="Не удалось обновить историю" message={error} retry={refresh} />}
      {loading && <Loading />}
      {applications.length > 0 && <ApplicationsTable applications={applications} />}
      {!loading && !error && applications.length === 0 && <EmptyState title="Заявок пока нет">Отправьте первую заявку — она появится здесь вместе со статусом.</EmptyState>}
    </section>
  </>;
}
