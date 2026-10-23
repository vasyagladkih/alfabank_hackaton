import { useCallback, useEffect, useRef, useState } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Plate } from '@alfalab/core-components-plate';
import { Status } from '@alfalab/core-components-status';
import { Table } from '@alfalab/core-components-table';
import { Typography } from '@alfalab/core-components-typography';
import { errorMessage, isAbortError } from '../../api/client';
import type { Application, DossierSource, EmployeeDossier } from '../../api/types';
import { useAuth } from '../../auth/AuthProvider';
import { ErrorNotice, Loading } from '../../components/Feedback';
import { ApplicationStatusBadge } from '../applications/ApplicationsTable';
import { formatDate, formatDecimalMoney, formatMoney, mergeApplications, PURPOSE_LABELS, STATUS_LABELS } from '../applications/model';
import { EmployeeDecisionForm } from './EmployeeDecisionForm';

const dayFormatter = new Intl.DateTimeFormat('ru-RU', { year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC' });
const numberFormatter = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });
const dateOnly = (value: string) => dayFormatter.format(new Date(`${value}T00:00:00Z`));
const moneyOrMissing = (value?: string | null) => value == null ? 'Нет данных' : formatDecimalMoney(value);
const freshness: Record<DossierSource['freshness'], { label: string; color: 'green' | 'orange' | 'grey' | 'red' }> = {
  FRESH: { label: 'Актуальные данные', color: 'green' },
  STALE: { label: 'Устаревшие данные', color: 'orange' },
  MISSING: { label: 'Нет данных', color: 'grey' },
  INVALID: { label: 'Некорректные данные', color: 'red' },
};
const findings = {
  HARD_STOP: { label: 'Стоп-фактор', color: 'red' },
  DATA_QUALITY: { label: 'Качество данных', color: 'orange' },
  WARNING: { label: 'Предупреждение', color: 'orange' },
  DECISION: { label: 'Результат расчёта', color: 'blue' },
} as const;
const actors = { SYSTEM: 'Система', EMPLOYEE: 'Сотрудник', CLIENT: 'Клиент' };

function DossierContents({ dossier }: { dossier: EmployeeDossier }) {
  const { application, assessment } = dossier;
  const sourceLabels = new Map(dossier.sources.map((item) => [item.code, item.label]));
  return <>
    <div className="dossier-overview">
      <div><span className="section-eyebrow">Заёмщик в демо</span><h3>{application.companyName || 'Компания не указана'}</h3><p>ИНН {application.inn || 'не указан'}</p></div>
      <dl className="dossier-key-values">
        <div><dt>Запрошено</dt><dd>{formatMoney(application.amount)}</dd></div>
        <div><dt>Срок</dt><dd>{application.termMonths} мес.</dd></div>
        <div><dt>Цель</dt><dd>{PURPOSE_LABELS[application.purpose]}</dd></div>
        <div><dt>Дата заявки</dt><dd><time dateTime={application.createdAt}>{formatDate(application.createdAt)}</time></dd></div>
      </dl>
    </div>

    <section className="dossier-section" aria-labelledby="dossier-risk-title">
      <div className="dossier-section-heading"><Typography.Title tag="h3" id="dossier-risk-title" view="xsmall" font="system" defaultMargins={false}>Учебная оценка риска</Typography.Title>
        {assessment && <span className="section-description">Правила {assessment.ruleVersion} · На {dateOnly(assessment.asOf)}</span>}</div>
      {assessment ? <>
        <div className="risk-summary">
          <div><span className="section-eyebrow">Балльная оценка</span><strong className="risk-score">{assessment.score == null ? 'Не рассчитана' : `${assessment.score} / ${assessment.maxScore}`}</strong></div>
          <div><span className="section-eyebrow">Результат автоматического расчёта</span><ApplicationStatusBadge status={assessment.outcome} /><p className="section-description">Оценка по demo-правилам, не вероятность дефолта.</p></div>
        </div>
        {!assessment.evidenceComplete && <p className="decision-lock">Для полной оценки недостаточно актуальных проверенных данных. Пропуски не считаются нулевыми значениями.</p>}
        <ul className="risk-findings">{assessment.findings.map((finding, index) => <li key={`${finding.code}-${index}`}>
          <Status color={findings[finding.severity].color} view="muted-alt" className="readable-status" uppercase={false}>{findings[finding.severity].label}</Status>
          <span>{finding.message}</span>
        </li>)}</ul>
        {assessment.components.length > 0 && <div className="table-scroll dossier-table-scroll" role="region" aria-label="Разбор балльной оценки" tabIndex={0}>
          <Table className="dossier-table score-table" wrapper={false} aria-label="Разбор балльной оценки">
            <Table.THead><Table.THeadCell scope="col">Фактор</Table.THeadCell><Table.THeadCell scope="col">Значение</Table.THeadCell><Table.THeadCell scope="col">Баллы</Table.THeadCell><Table.THeadCell scope="col">Объяснение</Table.THeadCell></Table.THead>
            <Table.TBody>{assessment.components.map((component) => <Table.TRow key={component.code}>
              <Table.TCell>{component.label}</Table.TCell><Table.TCell>{component.value}</Table.TCell><Table.TCell className="nowrap">{component.points} / {component.maximum}</Table.TCell><Table.TCell>{component.explanation}</Table.TCell>
            </Table.TRow>)}</Table.TBody>
          </Table>
        </div>}
        <div className="financial-summary">
          <h4>Финансовое сопоставление</h4>
          <dl className="dossier-key-values financial-values">
            <div><dt>Учебный доход для расчёта</dt><dd>{moneyOrMissing(assessment.monthlyIncome)}</dd></div>
            <div><dt>Платежи по всем обязательствам</dt><dd>{moneyOrMissing(assessment.existingDebtPayment)}</dd></div>
            <div><dt>Платёж по запросу</dt><dd>{moneyOrMissing(assessment.requestedPayment)}</dd></div>
            <div><dt>Расчётная нагрузка</dt><dd>{assessment.debtRatio == null ? 'Нет данных' : `${numberFormatter.format(Number(assessment.debtRatio) * 100)} %`}</dd></div>
            <div><dt>Доступная сумма по расчёту</dt><dd>{assessment.affordableAmount == null ? 'Нет данных' : formatMoney(assessment.affordableAmount)}</dd></div>
            <div><dt>Demo-ставка, годовых</dt><dd>{assessment.annualRate == null ? 'Нет данных' : `${numberFormatter.format(Number(assessment.annualRate))} %`}</dd></div>
          </dl>
          <p className="section-description">Доход и ограничения — настраиваемые допущения демонстратора. Платёж по существующим долгам объединяет внутренние и внешние обязательства.</p>
        </div>
      </> : <p className="empty-dossier-section">Оценка ещё не рассчитана</p>}
    </section>

    <section className="dossier-section" aria-labelledby="dossier-sources-title">
      <Typography.Title tag="h3" id="dossier-sources-title" view="xsmall" font="system" defaultMargins={false}>Источники и свежесть</Typography.Title>
      <p className="section-description">Демонстрация на синтетических данных. Внешние сервисы не запрашиваются.</p>
      {dossier.sources.length > 0 ? <div className="source-grid">{dossier.sources.map((source) => <article className="source-card" key={source.code}>
        <h4>{source.label}</h4><Status color={freshness[source.freshness].color} view="muted-alt" className="readable-status" uppercase={false}>{freshness[source.freshness].label}</Status>
        <p>{source.observedOn ? `На ${dateOnly(source.observedOn)}` : 'Дата наблюдения не указана'}</p>
        <p className="section-description">Синтетический источник{source.version ? ` · ${source.version}` : ''}</p>
      </article>)}</div> : <p className="empty-dossier-section">Источники для этой заявки пока не зафиксированы.</p>}
      {dossier.facts.length > 0 && <dl className="dossier-facts">{dossier.facts.map((fact) => <div key={fact.code}>
        <dt>{fact.label}<span className="fact-source">Источник: {sourceLabels.get(fact.source) || fact.source}</span></dt>
        <dd>{fact.value == null ? 'Нет данных' : `${fact.value}${fact.unit ? ` ${fact.unit}` : ''}`}</dd>
      </div>)}</dl>}
      {dossier.cashflows.length > 0 && <div className="table-scroll dossier-table-scroll" role="region" aria-label="Синтетические обороты по месяцам" tabIndex={0}>
        <Table className="dossier-table cashflow-table" wrapper={false} aria-label="Синтетические обороты по месяцам">
          <Table.THead><Table.THeadCell scope="col">Месяц</Table.THeadCell><Table.THeadCell scope="col" textAlign="right">Поступления, ₽</Table.THeadCell></Table.THead>
          <Table.TBody>{dossier.cashflows.map((item) => <Table.TRow key={item.month}><Table.TCell>{item.month}</Table.TCell><Table.TCell className="money-cell">{moneyOrMissing(item.turnover)}</Table.TCell></Table.TRow>)}</Table.TBody>
        </Table>
      </div>}
      <details className="snapshot-details"><summary>Сохранённый снимок входных данных</summary>
        {dossier.snapshotCapturedAt ? <><p>Зафиксирован {formatDate(dossier.snapshotCapturedAt)}{dossier.snapshotAsOf ? `, данные на ${dateOnly(dossier.snapshotAsOf)}` : ''}. Последующие изменения профиля не меняют этот снимок.</p>
          {dossier.snapshotOrigin && <p>Источник снимка: {dossier.snapshotOrigin}</p>}
          {dossier.snapshotSha256 && <p className="snapshot-hash">SHA-256: <code>{dossier.snapshotSha256}</code></p>}</> : <p>Снимок ещё не создан. Исторические оценки не восстанавливаются из текущего профиля.</p>}
      </details>
    </section>
  </>;
}

export function EmployeeDossierPanel({ id, onClose, onApplicationChanged }: {
  id: number; onClose: () => void; onApplicationChanged: (application: Application) => void;
}) {
  const { api } = useAuth();
  const [dossier, setDossier] = useState<EmployeeDossier | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const request = useRef<AbortController | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);

  const accept = useCallback((updated: EmployeeDossier) => {
    setDossier((current) => current && mergeApplications([current.application], [updated.application])[0] !== updated.application ? current : updated);
    onApplicationChanged(updated.application);
  }, [onApplicationChanged]);

  const refresh = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setLoading(true);
    setError(null);
    try {
      const updated = await api.getEmployeeDossier(id, controller.signal);
      if (controller.signal.aborted) return false;
      accept(updated);
      return true;
    } catch (cause) {
      if (!controller.signal.aborted && !isAbortError(cause)) setError(errorMessage(cause));
      return false;
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [api, id, accept]);

  useEffect(() => { void refresh(); return () => request.current?.abort(); }, [refresh]);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    heading.current?.scrollIntoView?.({ block: 'start' });
  }, [id]);

  return <section className="panel dossier-panel" aria-labelledby={`dossier-${id}-title`} aria-busy={loading}>
    <div className="dossier-header"><div className="section-heading">
      <span className="section-eyebrow">Учебное досье · Только синтетические источники</span>
      <h2 ref={heading} tabIndex={-1} id={`dossier-${id}-title`}>Досье заявки №{id}</h2>
      {dossier && <div className="dossier-current-status"><ApplicationStatusBadge status={dossier.application.status} /><span>Версия {dossier.application.version}</span></div>}
    </div><div className="dossier-header-actions"><Button view="secondary" size={40} disabled={loading} loading={loading} onClick={() => void refresh()}>Обновить досье</Button><Button view="text" size={40} onClick={onClose}>Закрыть досье</Button></div></div>
    {loading && <Loading>Загружаем досье…</Loading>}
    {error && <ErrorNotice title="Не удалось обновить досье" message={error} retry={() => void refresh()} />}
    {saved && <div className="dossier-saved"><Plate view="positive" rounded title="Учебное решение сохранено" titleView="bold">Новое состояние и основание доступны в аудите.</Plate></div>}
    {dossier && <>
      <DossierContents dossier={dossier} />
      <EmployeeDecisionForm dossier={dossier} api={api} loading={loading || Boolean(error)} onConflict={refresh}
        onUpdated={(updated) => { accept(updated); setSaved(true); }} />
      <section className="dossier-section" aria-labelledby="dossier-audit-title">
        <Typography.Title tag="h3" id="dossier-audit-title" view="xsmall" font="system" defaultMargins={false}>История изменений</Typography.Title>
        {dossier.audit.length ? <ol className="audit-list">{[...dossier.audit].sort((a, b) => b.version - a.version || b.id - a.id).map((event) => <li key={event.id}>
          <div className="audit-heading"><strong>{actors[event.actorRole]} · {STATUS_LABELS[event.toStatus]}</strong><time dateTime={event.createdAt}>{formatDate(event.createdAt)}</time></div>
          <p className="section-description">{event.fromStatus ? `${STATUS_LABELS[event.fromStatus]} → ${STATUS_LABELS[event.toStatus]}` : 'Заявка создана'} · Версия {event.version}</p>
          {event.rationale && <p>{event.rationale}</p>}
        </li>)}</ol> : <p className="empty-dossier-section">Изменения ещё не зафиксированы.</p>}
      </section>
    </>}
  </section>;
}
