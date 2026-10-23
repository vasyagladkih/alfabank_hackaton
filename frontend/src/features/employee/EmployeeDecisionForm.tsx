import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Input } from '@alfalab/core-components-input';
import { Typography } from '@alfalab/core-components-typography';
import type { ApiClient } from '../../api/client';
import { ApiError, errorMessage, isAbortError } from '../../api/client';
import type { EmployeeDecisionRequest, EmployeeDecisionType, EmployeeDossier } from '../../api/types';
import { ErrorNotice } from '../../components/Feedback';
import { NativeSelectField } from '../../components/NativeSelectField';
import { NativeTextAreaField } from '../../components/NativeTextAreaField';
import { formatMoney } from '../applications/model';

const labels: Record<EmployeeDecisionType, string> = {
  APPROVE: 'Одобрить исходные условия', REJECT: 'Отклонить заявку', COUNTEROFFER: 'Предложить меньшую сумму',
};

export function EmployeeDecisionForm({ dossier, api, loading, onUpdated, onConflict }: {
  dossier: EmployeeDossier; api: ApiClient; loading: boolean;
  onUpdated: (value: EmployeeDossier) => void; onConflict: () => Promise<boolean>;
}) {
  const [type, setType] = useState<EmployeeDecisionType>(dossier.allowedActions[0] ?? 'APPROVE');
  const [rationale, setRationale] = useState('');
  const [offerAmount, setOfferAmount] = useState(String(Math.min(dossier.application.amount, dossier.assessment?.affordableAmount ?? dossier.application.amount)));
  const [errors, setErrors] = useState<{ rationale?: string; offerAmount?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const request = useRef<AbortController | null>(null);
  const command = useRef<{ fingerprint: string; body: EmployeeDecisionRequest } | null>(null);
  useEffect(() => () => request.current?.abort(), [api]);

  const manual = dossier.application.status === 'MANUAL_REVIEW';
  const allowed = manual && dossier.allowedActions.includes(type);
  const disabled = submitting || loading || !manual;
  const cap = Math.min(dossier.application.amount, dossier.assessment?.affordableAmount ?? 0);
  const prefix = `decision-${dossier.application.id}`;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || !allowed || loading) return;
    const nextErrors: typeof errors = {};
    if (rationale.trim().length < 3 || rationale.trim().length > 1000) nextErrors.rationale = 'Укажите основание решения: от 3 до 1 000 символов.';
    const amount = Number(offerAmount);
    if (type === 'COUNTEROFFER' && (!offerAmount.trim() || !Number.isSafeInteger(amount) || amount < 100000 || amount > cap)) {
      nextErrors.offerAmount = `Введите целую сумму от 100 000 до ${formatMoney(cap)}.`;
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      document.getElementById(`${prefix}-${nextErrors.rationale ? 'rationale' : 'amount'}`)?.focus();
      return;
    }
    const draft = { expectedVersion: dossier.application.version, type, rationale: rationale.trim(), ...(type === 'COUNTEROFFER' ? { offerAmount: amount } : {}) };
    const fingerprint = JSON.stringify(draft);
    if (command.current?.fingerprint !== fingerprint) command.current = { fingerprint, body: { ...draft, requestId: crypto.randomUUID() } };
    const controller = new AbortController();
    request.current = controller;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await api.decideApplication(dossier.application.id, command.current.body, controller.signal);
      if (!controller.signal.aborted) onUpdated(updated);
    } catch (cause) {
      if (controller.signal.aborted || isAbortError(cause)) return;
      if (cause instanceof ApiError && cause.status === 409) {
        const refreshed = await onConflict();
        if (!controller.signal.aborted) setError(refreshed
          ? 'Заявка изменилась. Досье обновлено. Ваш текст сохранён — проверьте актуальное состояние перед повтором.'
          : 'Заявка изменилась, но обновить досье не удалось. Ваш текст сохранён. Повторите обновление.');
      } else {
        setError(`${errorMessage(cause)}${cause instanceof ApiError && (cause.status === 0 || cause.status >= 500) ? ' Результат сохранения не подтверждён. Обновите досье перед повтором.' : ''}`);
      }
    } finally {
      if (!controller.signal.aborted) setSubmitting(false);
      if (request.current === controller) request.current = null;
    }
  }

  return <section className="dossier-section decision-section" aria-labelledby={`${prefix}-title`}>
    <Typography.Title tag="h3" id={`${prefix}-title`} view="xsmall" font="system" defaultMargins={false}>Решение сотрудника</Typography.Title>
    <p className="section-description">Учебное решение по сохранённому снимку. Срок и demo-ставку задаёт сервер; сумма предложения не может превышать запрос и доступный лимит.</p>
    {!manual && <p className="decision-lock">В текущем статусе решения сотрудника недоступны. История изменений сохранена ниже.</p>}
    {manual && !dossier.assessment?.eligibleForManualApproval && <p className="decision-lock">Доступные действия ограничены результатами проверки данных. Одобрение без достаточных оснований недоступно.</p>}
    <form onSubmit={(event) => void submit(event)} noValidate aria-busy={submitting} className="decision-form">
      <NativeSelectField id={`${prefix}-type`} label="Учебное решение" value={type} disabled={disabled}
        options={(Object.entries(labels) as Array<[EmployeeDecisionType, string]>).map(([key, content]) => ({ key, content, disabled: !dossier.allowedActions.includes(key) }))}
        onChange={(value) => { setType(value as EmployeeDecisionType); setErrors({}); }} />
      {type === 'COUNTEROFFER' && <Input id={`${prefix}-amount`} label="Предлагаемая сумма, ₽" labelView="outer" block type="number" inputMode="numeric"
        min={100000} max={cap} step={1} value={offerAmount} disabled={disabled} required
        onChange={(_, { value }) => { setOfferAmount(value); setErrors((current) => ({ ...current, offerAmount: undefined })); }}
        hint={<span id={`${prefix}-amount-hint`}>Не более {formatMoney(cap)}. Платёж рассчитает сервер.</span>}
        error={errors.offerAmount ? <span id={`${prefix}-amount-error`}>{errors.offerAmount}</span> : undefined}
        aria-invalid={Boolean(errors.offerAmount)} aria-describedby={`${prefix}-amount-${errors.offerAmount ? 'error' : 'hint'}`} />}
      <NativeTextAreaField id={`${prefix}-rationale`} label="Основание решения" value={rationale} disabled={disabled} error={errors.rationale}
        onChange={(value) => { setRationale(value); setErrors((current) => ({ ...current, rationale: undefined })); }} />
      {error && <ErrorNotice title="Проверьте результат сохранения" message={error} />}
      <div className="decision-submit">
        <Button type="submit" size={48} view="accent" loading={submitting} disabled={disabled || !allowed}>Сохранить учебное решение</Button>
        <span className="section-description">Изменение и основание появятся в аудите. Реальных договоров и выдачи денег нет.</span>
      </div>
    </form>
  </section>;
}
