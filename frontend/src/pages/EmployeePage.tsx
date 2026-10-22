import { useEffect, useState } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Typography } from '@alfalab/core-components-typography';
import { useAuth } from '../auth/AuthProvider';
import type { Application, StatusFilter } from '../api/types';
import { errorMessage, isAbortError } from '../api/client';
import { IdentityBar } from '../components/Layout';
import { EmptyState, ErrorNotice, Loading } from '../components/Feedback';
import { NativeSelectField } from '../components/NativeSelectField';
import { ApplicationsTable } from '../features/applications/ApplicationsTable';
import { APPLICATION_STATUSES, mergeApplications, STATUS_LABELS } from '../features/applications/model';

const options = [{ key: 'ALL', content: 'Все' }, ...APPLICATION_STATUSES.map((key) => ({ key, content: STATUS_LABELS[key] }))];

export function EmployeePage() {
  const { api } = useAuth();
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ items: Application[]; loading: boolean; error: string | null }>({ items: [], loading: true, error: null });
  const refresh = () => setRevision((value) => value + 1);

  useEffect(() => {
    const controller = new AbortController();
    setState({ items: [], loading: true, error: null });
    void api.getEmployeeApplications(filter, controller.signal).then((items) => {
      if (!controller.signal.aborted) setState({ items: mergeApplications(items), loading: false, error: null });
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted && !isAbortError(cause)) setState({ items: [], loading: false, error: errorMessage(cause) });
    });
    return () => controller.abort();
  }, [api, filter, revision]);

  return <>
    <IdentityBar />
    <div className="page-heading">
      <Typography.Text view="caps" color="secondary">Кредитный конвейер</Typography.Text>
      <Typography.TitleResponsive tag="h1" view="medium" font="system" defaultMargins={false}>Заявки на финансирование</Typography.TitleResponsive>
      <Typography.Text tag="p" view="primary-medium" color="secondary" defaultMargins={false}>Реестр заявок малого и микробизнеса. Следите за результатами обработки.</Typography.Text>
    </div>
    <section className="panel registry-panel" aria-labelledby="registry-title">
      <div className="section-toolbar registry-toolbar">
        <div className="section-heading">
          <Typography.Title tag="h2" id="registry-title" view="small" font="system" defaultMargins={false}>Мониторинг заявок</Typography.Title>
          <Typography.Text view="primary-small" color="secondary">{state.loading ? 'Загружаем реестр…' : state.error ? 'Не удалось получить реестр' : `Найдено заявок: ${state.items.length}`}</Typography.Text>
        </div>
        <div className="registry-controls">
          <NativeSelectField id="status-filter" label="Статус заявки" value={filter} options={options}
            onChange={(value) => setFilter(value as StatusFilter)} />
          <Button view="secondary" size={48} loading={state.loading} disabled={state.loading} aria-label="Обновить" onClick={refresh}>Обновить</Button>
        </div>
      </div>
      {state.loading && <Loading />}
      {state.error && <ErrorNotice title="Не удалось загрузить заявки" message={state.error} retry={refresh} />}
      {!state.loading && !state.error && (state.items.length > 0 ? <ApplicationsTable applications={state.items} employee /> :
        <EmptyState title="Заявок с таким статусом пока нет">Выберите другой статус или обновите реестр позже.</EmptyState>)}
    </section>
  </>;
}
