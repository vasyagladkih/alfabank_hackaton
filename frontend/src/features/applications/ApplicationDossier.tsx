import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Plate } from '@alfalab/core-components-plate';
import { Typography } from '@alfalab/core-components-typography';
import type { Application, ApplicationDetails } from '../../api/types';
import { ApiError, errorMessage, isAbortError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { ErrorNotice, Loading } from '../../components/Feedback';
import { ApplicationStatusBadge } from './ApplicationsTable';
import { formatDate, formatMoney, PURPOSE_LABELS } from './model';
import { PaymentScheduleView } from './PaymentScheduleView';

function Detail({ label, value }: { label: string; value?: ReactNode }) {
  return <div><dt>{label}</dt><dd>{value ?? '—'}</dd></div>;
}

const optionalMoney = (value?: number | null) => value == null ? undefined : formatMoney(value);

export function ApplicationDossier({ applicationId, onUpdated }: { applicationId: number; onUpdated: (application: Application) => void }) {
  const { api } = useAuth();
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ data: ApplicationDetails | null; loading: boolean; error: string | null }>({ data: null, loading: true, error: null });
  const [retryState, setRetryState] = useState<{
    pending: boolean; error: string | null; requiresRefresh: boolean; succeeded: boolean;
  }>({ pending: false, error: null, requiresRefresh: false, succeeded: false });
  const retryController = useRef<AbortController | null>(null);
  const refresh = () => {
    setRevision((value) => value + 1);
  };

  useEffect(() => {
    setState({ data: null, loading: true, error: null });
    setRetryState({ pending: false, error: null, requiresRefresh: false, succeeded: false });
    return () => {
      retryController.current?.abort();
      retryController.current = null;
    };
  }, [api, applicationId]);

  useEffect(() => {
    const controller = new AbortController();
    setState((current) => ({ data: current.data?.id === applicationId ? current.data : null, loading: true, error: null }));
    void api.getEmployeeApplicationDetails(applicationId, controller.signal).then((data) => {
      if (!controller.signal.aborted) {
        setState({ data, loading: false, error: null });
        setRetryState((current) => ({ ...current, error: null, requiresRefresh: false }));
      }
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted && !isAbortError(cause)) {
        setState((current) => ({ ...current, loading: false, error: errorMessage(cause) }));
      }
    });
    return () => controller.abort();
  }, [api, applicationId, revision]);

  const data = state.data?.id === applicationId ? state.data : null;
  const preliminary = data?.status === 'MANUAL_REVIEW';
  const technicalFailure = data?.status === 'MANUAL_REVIEW' && data.reviewReasonCode === 'TECH_FAILURE';
  const canRetry = technicalFailure && data.canRetry === true;
  const retry = () => {
    if (!canRetry || state.loading || retryState.requiresRefresh || retryController.current) return;
    const controller = new AbortController();
    retryController.current = controller;
    setRetryState({ pending: true, error: null, requiresRefresh: false, succeeded: false });
    void api.retryApplication(applicationId, controller.signal).then((application) => {
      if (controller.signal.aborted) return;
      setState((current) => ({
        data: {
          ...current.data, ...application,
          approvedAmount: application.approvedAmount, approvedRate: application.approvedRate,
          schedule: application.schedule, rejectionReason: application.rejectionReason,
          updatedAt: application.updatedAt, reviewReasonCode: undefined, canRetry: false,
        },
        loading: false, error: null,
      }));
      setRetryState({ pending: false, error: null, requiresRefresh: true, succeeded: true });
      onUpdated(application);
      refresh();
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted && !isAbortError(cause)) {
        const requiresRefresh = !(cause instanceof ApiError) || cause.status === 0 || cause.status >= 500 || cause.code === 'INVALID_RESPONSE';
        setRetryState({
          pending: false, requiresRefresh, succeeded: false,
          error: `${errorMessage(cause)}${requiresRefresh ? ' Результат проверки неизвестен. Обновите досье перед повторным запуском: проверка могла выполниться на сервере.' : ''}`,
        });
      }
    }).finally(() => {
      if (retryController.current === controller) retryController.current = null;
    });
  };

  return <section className="panel details-panel" aria-labelledby={`dossier-${applicationId}-title`}>
    <div className="section-toolbar">
      <Typography.Title tag="h2" id={`dossier-${applicationId}-title`} view="small" font="system" defaultMargins={false}>Досье заявки №{applicationId}</Typography.Title>
      <Button view="secondary" size={40} loading={state.loading} disabled={state.loading || retryState.pending} onClick={refresh}>Обновить досье</Button>
    </div>
    {state.loading && <Loading>{data ? 'Обновляем досье…' : 'Загружаем досье…'}</Loading>}
    {state.error && <ErrorNotice title={data ? 'Не удалось обновить досье' : 'Не удалось загрузить досье'} message={state.error} retry={refresh} />}
    {retryState.succeeded && <div role="status">Результат повторной проверки получен. Статус заявки обновлён.</div>}
    {data && <>
      {technicalFailure && <Plate view="attention" title="Технический сбой проверки" titleView="bold" rounded limitContentWidth={false}
        buttons={canRetry ? <Button size={40} view="secondary" loading={retryState.pending} disabled={retryState.pending || state.loading || retryState.requiresRefresh} onClick={retry}>Перезапустить проверку</Button> : undefined}>
        {data.rejectionReason || 'Проверка завершилась технической ошибкой.'}
      </Plate>}
      {retryState.error && <ErrorNotice title="Не удалось перезапустить проверку" message={retryState.error} />}
      <section aria-labelledby={`dossier-${applicationId}-application`}>
        <Typography.Title tag="h3" id={`dossier-${applicationId}-application`} view="xsmall" font="system" defaultMargins={false}>Данные заявки</Typography.Title>
        <dl className="details-grid">
          <Detail label="Заёмщик" value={data.companyName || data.user?.companyName || undefined} />
          <Detail label="ИНН" value={data.inn || data.user?.inn || undefined} />
          <Detail label="Статус" value={<ApplicationStatusBadge status={data.status} />} />
          <Detail label="Запрошенная сумма" value={formatMoney(data.amount)} />
          <Detail label="Срок" value={`${data.termMonths} мес.`} />
          <Detail label="Цель" value={PURPOSE_LABELS[data.purpose]} />
          <Detail label={preliminary ? 'Предварительная сумма' : 'Одобренная сумма'} value={optionalMoney(data.approvedAmount)} />
          <Detail label={preliminary ? 'Предварительная ставка' : 'Ставка'} value={data.approvedRate == null ? undefined : `${data.approvedRate}%`} />
          <Detail label="Создана" value={formatDate(data.createdAt)} />
          <Detail label="Обновлена" value={data.updatedAt ? formatDate(data.updatedAt) : undefined} />
          {!technicalFailure && data.rejectionReason && <Detail label="Комментарий к решению" value={data.rejectionReason} />}
        </dl>
        {preliminary && <Typography.Text tag="p" view="primary-small" color="secondary">
          Условия предварительные; окончательное решение принимает сотрудник банка. Подписание пока недоступно.
        </Typography.Text>}
      </section>
      <section aria-labelledby={`dossier-${applicationId}-fns`}>
        <Typography.Title tag="h3" id={`dossier-${applicationId}-fns`} view="xsmall" font="system" defaultMargins={false}>ФНС</Typography.Title>
        {data.fns ? <dl className="details-grid">
          <Detail label="Статус в ФНС" value={data.fns.stateStatus} />
          <Detail label="Возраст бизнеса, мес." value={data.fns.businessAgeMonths} />
          <Detail label="Блокировки счетов" value={data.fns.taxBlocksExist == null ? undefined : data.fns.taxBlocksExist ? 'Да' : 'Нет'} />
        </dl> : <Typography.Text tag="p" color="secondary">Нет данных</Typography.Text>}
      </section>
      <section aria-labelledby={`dossier-${applicationId}-bki`}>
        <Typography.Title tag="h3" id={`dossier-${applicationId}-bki`} view="xsmall" font="system" defaultMargins={false}>БКИ</Typography.Title>
        {data.bki ? <dl className="details-grid">
          <Detail label="Балл БКИ" value={data.bki.bkiScore} />
          <Detail label="Текущие платежи в месяц" value={optionalMoney(data.bki.monthlyPaymentRub)} />
          <Detail label="Текущая просрочка, дней" value={data.bki.currentOverdueDays} />
          <Detail label="Просрочки свыше 90 дней" value={data.bki.cntOverdueOver90d} />
          <Detail label="Категория риска" value={data.bki.riskBucket} />
        </dl> : <Typography.Text tag="p" color="secondary">Нет данных</Typography.Text>}
      </section>
      <section aria-labelledby={`dossier-${applicationId}-scoring`}>
        <Typography.Title tag="h3" id={`dossier-${applicationId}-scoring`} view="xsmall" font="system" defaultMargins={false}>Скоринг</Typography.Title>
        {data.scoring ? <dl className="details-grid">
          <Detail label="Скоринговый балл" value={data.scoring.scoringScore} />
          <Detail label="ПДН" value={data.scoring.calculatedPdnPercent == null ? undefined : `${data.scoring.calculatedPdnPercent}%`} />
          <Detail label="Максимальный лимит" value={optionalMoney(data.scoring.maxLimitRub)} />
        </dl> : <Typography.Text tag="p" color="secondary">Нет данных</Typography.Text>}
      </section>
      {data.schedule && <PaymentScheduleView schedule={data.schedule} preliminary={preliminary} />}
    </>}
  </section>;
}
