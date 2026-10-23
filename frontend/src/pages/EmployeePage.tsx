import { useCallback, useEffect, useRef, useState } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Input } from '@alfalab/core-components-input';
import { Typography } from '@alfalab/core-components-typography';
import { useAuth } from '../auth/AuthProvider';
import type { Application, StatusFilter } from '../api/types';
import { errorMessage, isAbortError } from '../api/client';
import { IdentityBar } from '../components/Layout';
import { EmptyState, ErrorNotice, Loading } from '../components/Feedback';
import { NativeSelectField } from '../components/NativeSelectField';
import { ApplicationsTable } from '../features/applications/ApplicationsTable';
import { APPLICATION_STATUSES, mergeApplications, STATUS_LABELS } from '../features/applications/model';
import { EmployeeDossierPanel } from '../features/employee/EmployeeDossierPanel';

const options = [{ key: 'ALL', content: 'Все' }, ...APPLICATION_STATUSES.map((key) => ({ key, content: STATUS_LABELS[key] }))];

export function EmployeePage() {
  const { api } = useAuth();
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ items: Application[]; loading: boolean; error: string | null }>({ items: [], loading: true, error: null });
  const queueRequest = useRef<AbortController | null>(null);
  const refresh = useCallback(() => setRevision((value) => value + 1), []);
  const updateApplication = useCallback((application: Application) => {
    // A queue read started before this dossier update may contain an older
    // version. Restart it after the update, preserving the current query/filter.
    if (queueRequest.current) {
      queueRequest.current.abort();
      queueRequest.current = null;
      refresh();
    }
    setState((current) => ({ ...current, items: mergeApplications(current.items, [application]).filter((item) =>
      (filter === 'ALL' || item.status === filter) && (!query || [String(item.id), item.companyName ?? '', item.inn ?? ''].some((value) => value.toLocaleLowerCase('ru-RU').includes(query.toLocaleLowerCase('ru-RU'))))) }));
  }, [filter, query, refresh]);

  useEffect(() => {
    const controller = new AbortController();
    queueRequest.current = controller;
    setState({ items: [], loading: true, error: null });
    void api.getEmployeeApplications(filter, controller.signal, query).then((items) => {
      if (!controller.signal.aborted) setState({ items: mergeApplications(items), loading: false, error: null });
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted && !isAbortError(cause)) setState({ items: [], loading: false, error: errorMessage(cause) });
    }).finally(() => {
      if (queueRequest.current === controller) queueRequest.current = null;
    });
    return () => {
      controller.abort();
      if (queueRequest.current === controller) queueRequest.current = null;
    };
  }, [api, filter, query, revision]);

  return <>
    <IdentityBar />
    <div className="page-heading">
      <Typography.Text view="caps" color="secondary">Кредитный конвейер</Typography.Text>
      <Typography.TitleResponsive tag="h1" view="medium" font="system" defaultMargins={false}>Заявки на финансирование</Typography.TitleResponsive>
      <Typography.Text tag="p" view="primary-medium" color="secondary" defaultMargins={false}>Реестр синтетических заявок. Откройте досье, изучите причины и сохраните учебное решение.</Typography.Text>
    </div>
    <section className="panel registry-panel" aria-labelledby="registry-title">
      <div className="section-toolbar registry-toolbar">
        <div className="section-heading">
          <Typography.Title tag="h2" id="registry-title" view="small" font="system" defaultMargins={false}>Очередь заявок</Typography.Title>
          <Typography.Text view="primary-small" color="secondary">{state.loading ? 'Загружаем реестр…' : state.error ? 'Не удалось получить реестр' : `Найдено заявок: ${state.items.length}`}</Typography.Text>
        </div>
        <div className="registry-controls">
          <NativeSelectField id="status-filter" label="Статус заявки" value={filter} options={options}
            onChange={(value) => setFilter(value as StatusFilter)} />
          <Button view="secondary" size={48} loading={state.loading} disabled={state.loading} aria-label="Обновить" onClick={refresh}>Обновить</Button>
        </div>
      </div>
      <form className="registry-search" onSubmit={(event) => { event.preventDefault(); setQuery(search.trim()); refresh(); }} role="search" aria-label="Поиск по реестру">
        <Input id="employee-search" type="text" inputMode="search" label="Поиск по заявкам" labelView="outer" value={search} maxLength={120} block
          onChange={(_, { value }) => setSearch(value)} placeholder="ID, компания или ИНН" />
        <Button type="submit" view="secondary" size={48} disabled={state.loading}>Найти</Button>
      </form>
      <p className="registry-help">Выберите номер заявки, чтобы открыть досье. Все источники и решения — демонстрационные.</p>
      {state.loading && <Loading />}
      {state.error && <ErrorNotice title="Не удалось загрузить заявки" message={state.error} retry={refresh} />}
      {!state.loading && !state.error && (state.items.length > 0 ? <ApplicationsTable applications={state.items} employee onOpen={setSelectedId} /> :
        <EmptyState title="Заявки не найдены">Измените поиск или статус и обновите реестр.</EmptyState>)}
    </section>
    {selectedId !== null && <EmployeeDossierPanel key={selectedId} id={selectedId} onApplicationChanged={updateApplication} onClose={() => {
      const trigger = document.getElementById(`open-dossier-${selectedId}`) ?? document.getElementById('employee-search');
      setSelectedId(null);
      trigger?.focus();
    }} />}
  </>;
}
