import { useCallback, useEffect, useState } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Typography } from '@alfalab/core-components-typography';
import { useAuth } from '../auth/AuthProvider';
import type { Application, ApplicationPage, StatusFilter } from '../api/types';
import { errorMessage, isAbortError } from '../api/client';
import { IdentityBar } from '../components/Layout';
import { EmptyState, ErrorNotice, Loading } from '../components/Feedback';
import { NativeSelectField } from '../components/NativeSelectField';
import { ApplicationDossier } from '../features/applications/ApplicationDossier';
import { DossierDrawer } from '../features/applications/DossierDrawer';
import { ApplicationsTable } from '../features/applications/ApplicationsTable';
import { APPLICATION_STATUSES, STATUS_LABELS } from '../features/applications/model';

const options = [{ key: 'ALL', content: 'Все' }, ...APPLICATION_STATUSES.map((key) => ({ key, content: STATUS_LABELS[key] }))];

export function EmployeePage() {
  const { api } = useAuth();
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ data: ApplicationPage | null; loading: boolean; error: string | null }>({ data: null, loading: true, error: null });
  const refresh = () => setRevision((value) => value + 1);
  const closeDossier = useCallback(() => setSelectedId(null), []);
  const applicationUpdated = (application: Application) => {
    setState((current) => current.data ? { ...current, data: {
      ...current.data, items: current.data.items.map((item) => item.id === application.id ? application : item),
    } } : current);
    refresh();
  };

  useEffect(() => { setSelectedId(null); }, [api]);

  useEffect(() => {
    const controller = new AbortController();
    setState({ data: null, loading: true, error: null });
    void api.getEmployeeApplications(filter, page, 10, controller.signal).then((data) => {
      if (controller.signal.aborted) return;
      const lastPage = Math.max(0, data.totalPages - 1);
      if (page > lastPage) { setPage(lastPage); return; }
      setState({ data, loading: false, error: null });
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted && !isAbortError(cause)) setState({ data: null, loading: false, error: errorMessage(cause) });
    });
    return () => controller.abort();
  }, [api, filter, page, revision]);

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
          <Typography.Title tag="h2" id="registry-title" tabIndex={-1} view="small" font="system" defaultMargins={false}>Мониторинг заявок</Typography.Title>
          <Typography.Text view="primary-small" color="secondary">{state.loading ? 'Загружаем реестр…' : state.error ? 'Не удалось получить реестр' : `Найдено заявок: ${state.data?.totalElements}`}</Typography.Text>
        </div>
        <div className="registry-controls">
          <NativeSelectField id="status-filter" label="Статус заявки" value={filter} options={options}
            onChange={(value) => { closeDossier(); setFilter(value as StatusFilter); setPage(0); }} />
          <Button view="secondary" size={48} loading={state.loading} disabled={state.loading} aria-label="Обновить" onClick={refresh}>Обновить</Button>
        </div>
      </div>
      {state.loading && <Loading />}
      {state.error && <ErrorNotice title="Не удалось загрузить заявки" message={state.error} retry={refresh} />}
      {state.data && <>
        {state.data.items.length > 0 ? <ApplicationsTable applications={state.data.items} employee onSelect={(application) => setSelectedId(application.id)} /> :
          <EmptyState title="Заявок с таким статусом пока нет">Выберите другой статус или обновите реестр позже.</EmptyState>}
        <nav className="section-toolbar" aria-label="Страницы реестра">
          <Typography.Text view="primary-small" color="secondary">Страница {state.data.totalPages === 0 ? 0 : state.data.page + 1} из {state.data.totalPages}</Typography.Text>
          <div className="pagination-controls">
            <Button view="secondary" size={40} disabled={state.loading || page === 0} onClick={() => { closeDossier(); setPage((value) => value - 1); }}>Назад</Button>
            <Button view="secondary" size={40} disabled={state.loading || page + 1 >= state.data.totalPages} onClick={() => { closeDossier(); setPage((value) => value + 1); }}>Далее</Button>
          </div>
        </nav>
      </>}
    </section>
    {selectedId !== null && <DossierDrawer key={selectedId} applicationId={selectedId} onClose={closeDossier}>
      <ApplicationDossier applicationId={selectedId} onUpdated={applicationUpdated} />
    </DossierDrawer>}
  </>;
}
