import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Input } from '@alfalab/core-components-input';
import { Typography } from '@alfalab/core-components-typography';
import { ArrowRightMIcon } from '@alfalab/icons-glyph/ArrowRightMIcon';
import type { Application, LoanPurpose } from '../../api/types';
import { ApiError, errorMessage, isAbortError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { ErrorNotice } from '../../components/Feedback';
import { NativeSelectField } from '../../components/NativeSelectField';
import { PURPOSE_LABELS } from './model';
import { validateApplication } from './validation';
import type { ApplicationErrors, ApplicationFields } from './validation';

const purposeOptions = Object.entries(PURPOSE_LABELS).map(([key, content]) => ({ key, content }));

export function ApplicationForm({ onCreated }: { onCreated: (application: Application) => void }) {
  const { api } = useAuth();
  const [fields, setFields] = useState<ApplicationFields>({ amount: '1500000', termMonths: '12', purpose: 'WORKING_CAPITAL' });
  const [errors, setErrors] = useState<ApplicationErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), [api]);

  const change = (key: keyof ApplicationFields, value: string) => {
    setFields((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  };

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current) return;
    const nextErrors = validateApplication(fields);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      const first = Object.keys(nextErrors)[0];
      document.getElementById(`application-${first}`)?.focus();
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    setSubmitting(true);
    setError(null);
    try {
      const result = await api.createApplication({ amount: Number(fields.amount), termMonths: Number(fields.termMonths), purpose: fields.purpose as LoanPurpose }, controller.signal);
      if (!controller.signal.aborted) onCreated(result);
    } catch (cause) {
      if (!controller.signal.aborted && !isAbortError(cause)) {
        const uncertain = cause instanceof ApiError && (cause.status === 0 || cause.status >= 500);
        setError(`${errorMessage(cause)}${uncertain ? ' Перед повторной отправкой обновите историю: заявка могла быть принята сервером.' : ''}`);
      }
    } finally {
      if (!controller.signal.aborted) setSubmitting(false);
      if (request.current === controller) request.current = null;
    }
  }

  return <form className="application-form" onSubmit={(event) => void submit(event)} noValidate aria-labelledby="application-form-title" aria-busy={submitting}>
    <div className="section-heading">
      <Typography.Title tag="h2" id="application-form-title" view="small" font="system" defaultMargins={false}>Новая заявка</Typography.Title>
      <Typography.Text view="primary-small" color="secondary">Укажите параметры финансирования</Typography.Text>
    </div>
    <div className="form-fields">
      <Input id="application-amount" name="amount" label="Сумма кредита, ₽" labelView="outer" type="number" inputMode="numeric"
        value={fields.amount} onChange={(_, { value }) => change('amount', value)} min={100000} max={50000000} step={1} required block size={56} disabled={submitting}
        hint={<span id="amount-hint">От 100 000 до 50 000 000 ₽</span>}
        error={errors.amount ? <span id="amount-error">{errors.amount}</span> : undefined}
        aria-invalid={Boolean(errors.amount)} aria-describedby={errors.amount ? 'amount-error' : 'amount-hint'} />
      <Input id="application-termMonths" name="termMonths" label="Срок, месяцев" labelView="outer" type="number" inputMode="numeric"
        value={fields.termMonths} onChange={(_, { value }) => change('termMonths', value)} min={1} max={60} step={1} required block size={56} disabled={submitting}
        hint={<span id="term-hint">От 1 до 60 месяцев</span>}
        error={errors.termMonths ? <span id="term-error">{errors.termMonths}</span> : undefined}
        aria-invalid={Boolean(errors.termMonths)} aria-describedby={errors.termMonths ? 'term-error' : 'term-hint'} />
      <NativeSelectField id="application-purpose" name="purpose" label="Цель кредита" options={purposeOptions}
        value={fields.purpose} onChange={(value) => change('purpose', value)}
        size={56} disabled={submitting} required error={errors.purpose} />
    </div>
    {error && <ErrorNotice title="Отправка заявки" message={error} />}
    <div className="form-submit">
      <Button type="submit" aria-label="Отправить заявку" view="accent" size={56} loading={submitting} disabled={submitting} rightAddons={<ArrowRightMIcon aria-hidden="true" />}>Отправить заявку</Button>
      <Typography.Text view="secondary-medium" color="secondary">Результат появится здесь после обработки заявки.</Typography.Text>
    </div>
  </form>;
}
