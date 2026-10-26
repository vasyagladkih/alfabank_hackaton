import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Input } from '@alfalab/core-components-input';
import { Typography } from '@alfalab/core-components-typography';
import type { Application } from '../../api/types';
import { ApiError, errorMessage, isAbortError } from '../../api/client';
import { useAuth } from '../../auth/AuthProvider';
import { ErrorNotice } from '../../components/Feedback';
import { formatMoney } from './model';
import { PaymentScheduleView } from './PaymentScheduleView';

export function ApplicationAgreement({ application, onUpdated, onRefresh }: {
  application: Application;
  onUpdated: (application: Application) => void;
  onRefresh: () => void;
}) {
  const { api } = useAuth();
  const [confirmTerms, setConfirmTerms] = useState(false);
  const [smsCode, setSmsCode] = useState('');
  const [errors, setErrors] = useState<{ confirmTerms?: string; smsCode?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const request = useRef<AbortController | null>(null);
  useEffect(() => {
    setSubmitting(false);
    return () => {
      request.current?.abort();
      request.current = null;
    };
  }, [api]);
  const prefix = `agreement-${application.id}`;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || application.status !== 'APPROVED') return;
    const nextErrors = {
      ...(!confirmTerms ? { confirmTerms: 'Подтвердите согласие с условиями договора.' } : {}),
      ...(!smsCode.trim() ? { smsCode: 'Введите код из СМС.' } : {}),
    };
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      document.getElementById(`${prefix}-${!confirmTerms ? 'confirmTerms' : 'smsCode'}`)?.focus();
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    setSubmitting(true);
    setError(null);
    setUncertain(false);
    try {
      const result = await api.signApplication(application.id, { confirmTerms, smsCode }, controller.signal);
      if (!controller.signal.aborted) onUpdated(result);
    } catch (cause) {
      if (!controller.signal.aborted && !isAbortError(cause)) {
        const mayBeSigned = cause instanceof ApiError && (cause.status === 0 || cause.status >= 500);
        setUncertain(mayBeSigned);
        setError(`${errorMessage(cause)}${mayBeSigned ? ' Перед повторной отправкой обновите историю: договор мог быть подписан сервером.' : ''}`);
      }
    } finally {
      if (!controller.signal.aborted) setSubmitting(false);
      if (request.current === controller) request.current = null;
    }
  }

  return <section className="panel details-panel" aria-labelledby={`${prefix}-title`}>
    <Typography.Title tag="h2" id={`${prefix}-title`} view="small" font="system" defaultMargins={false}>Условия заявки №{application.id}</Typography.Title>
    <dl className="details-grid">
      <div><dt>Сумма кредита</dt><dd>{application.approvedAmount == null ? 'Не указана' : formatMoney(application.approvedAmount)}</dd></div>
      <div><dt>Ставка</dt><dd>{application.approvedRate == null ? 'Не указана' : `${application.approvedRate} % годовых`}</dd></div>
      <div><dt>Срок</dt><dd>{application.termMonths} мес.</dd></div>
    </dl>
    {application.schedule ? <PaymentScheduleView schedule={application.schedule} />
      : <Typography.Text tag="p" view="primary-small" color="secondary" defaultMargins={false}>График платежей пока недоступен.</Typography.Text>}
    {application.status === 'SIGNED' && <div role="status"><Typography.Text tag="p" view="primary-medium" defaultMargins={false}>Договор подписан. Статус сохранён в истории.</Typography.Text></div>}
    {application.status === 'APPROVED' && <form className="application-form" onSubmit={(event) => void submit(event)} noValidate aria-label="Подписание договора" aria-busy={submitting}>
      <div className="form-fields">
        <div>
          <label className="consent-field" htmlFor={`${prefix}-confirmTerms`}>
            <input id={`${prefix}-confirmTerms`} name="confirmTerms" type="checkbox" checked={confirmTerms} required disabled={submitting}
              onChange={(event) => {
                setConfirmTerms(event.target.checked);
                setErrors((current) => ({ ...current, confirmTerms: undefined }));
              }} aria-invalid={Boolean(errors.confirmTerms)} aria-describedby={errors.confirmTerms ? `${prefix}-consent-error` : undefined} />
            <span>Согласен с условиями договора</span>
          </label>
          {errors.confirmTerms && <Typography.Text tag="p" id={`${prefix}-consent-error`} view="primary-small" color="negative" defaultMargins={false}>{errors.confirmTerms}</Typography.Text>}
        </div>
        <Input id={`${prefix}-smsCode`} name="smsCode" label="Код из СМС" labelView="outer" value={smsCode}
          onChange={(_, { value }) => {
            setSmsCode(value);
            setErrors((current) => ({ ...current, smsCode: undefined }));
          }} required block size={56} disabled={submitting} autoComplete="one-time-code"
          error={errors.smsCode ? <span id={`${prefix}-sms-error`}>{errors.smsCode}</span> : undefined}
          aria-invalid={Boolean(errors.smsCode)} aria-describedby={errors.smsCode ? `${prefix}-sms-error` : undefined} />
      </div>
      {error && <ErrorNotice title="Подписание договора" message={error} />}
      {uncertain && <Button type="button" view="secondary" size={40} onClick={onRefresh}>Обновить историю</Button>}
      <div className="form-submit"><Button type="submit" view="accent" size={56} loading={submitting} disabled={submitting}>Подписать договор</Button></div>
    </form>}
  </section>;
}
