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

export function ApplicationAgreement({ application, onUpdated, onRefresh, requiresReconciliation = false, onReconciliationChange }: {
  application: Application;
  onUpdated: (application: Application) => void;
  onRefresh: () => void;
  requiresReconciliation?: boolean;
  onReconciliationChange?: (required: boolean) => void;
}) {
  const { api } = useAuth();
  const [confirmTerms, setConfirmTerms] = useState(false);
  const [smsCode, setSmsCode] = useState('');
  const [errors, setErrors] = useState<{ confirmTerms?: string; smsCode?: string }>({});
  const [error, setError] = useState<string | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [reconciling, setReconciling] = useState(false);
  const request = useRef<AbortController | null>(null);
  useEffect(() => {
    setConfirmTerms(false);
    setSmsCode('');
    setErrors({});
    setError(null);
    setUncertain(false);
    setSubmitting(false);
    setReconciling(false);
    return () => {
      request.current?.abort();
      request.current = null;
    };
  }, [api, application.id]);
  const prefix = `agreement-${application.id}`;
  const validCode = /^[0-9]{4}$/.test(smsCode);
  const busy = submitting || reconciling;
  const needsReconciliation = uncertain || requiresReconciliation;
  const signingError = error ?? (!submitting && requiresReconciliation ? 'Сначала обновите историю: результат предыдущей попытки подписания неизвестен.' : null);
  const counteroffer = application.status === 'APPROVED' && application.approvedAmount != null && application.approvedAmount < application.amount;
  const manualReview = application.status === 'MANUAL_REVIEW';
  const bankComment = application.status === 'APPROVED' ? application.rejectionReason?.trim() : undefined;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || needsReconciliation || application.status !== 'APPROVED') return;
    const nextErrors = {
      ...(!confirmTerms ? { confirmTerms: 'Подтвердите согласие с условиями договора.' } : {}),
      ...(!validCode ? { smsCode: smsCode ? 'Введите 4 цифры кода из СМС.' : 'Введите код из СМС.' } : {}),
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
    // Closing the selected agreement can abort the response while the server
    // still commits the signature. The parent retains this requirement.
    onReconciliationChange?.(true);
    try {
      const result = await api.signApplication(application.id, { confirmTerms, smsCode }, controller.signal);
      if (result.id !== application.id || result.status !== 'SIGNED') {
        throw new ApiError(502, 'Сервер не подтвердил подписание договора.', 'INVALID_RESPONSE');
      }
      if (!controller.signal.aborted) {
        onUpdated(result);
        onReconciliationChange?.(false);
      }
    } catch (cause) {
      if (!controller.signal.aborted && !isAbortError(cause)) {
        const unambiguousRejection = cause instanceof ApiError && cause.status >= 400 && cause.status < 500;
        const mayBeSigned = !unambiguousRejection;
        setUncertain(mayBeSigned);
        if (!mayBeSigned) onReconciliationChange?.(false);
        setError(`${errorMessage(cause)}${mayBeSigned ? ' Перед повторной отправкой обновите историю: договор мог быть подписан сервером.' : ''}`);
      }
    } finally {
      if (!controller.signal.aborted) setSubmitting(false);
      if (request.current === controller) request.current = null;
    }
  }

  async function reconcile() {
    if (request.current || !needsReconciliation) return;
    const controller = new AbortController();
    request.current = controller;
    setReconciling(true);
    try {
      const result = await api.getApplication(application.id, controller.signal);
      if (result.id !== application.id) {
        throw new ApiError(502, 'Ответ сервера относится к другой заявке.', 'INVALID_RESPONSE');
      }
      if (controller.signal.aborted) return;
      onUpdated(result);
      onReconciliationChange?.(false);
      onRefresh();
      // A confirmed APPROVED state permits another deliberate signing attempt.
      // Other states remain blocked until the parent displays the new status.
      setUncertain(result.status !== 'APPROVED');
      setError(null);
    } catch (cause) {
      if (!controller.signal.aborted && !isAbortError(cause)) {
        setError(`Не удалось проверить состояние договора. ${errorMessage(cause)} Перед повторной отправкой обновите историю.`);
      }
    } finally {
      if (!controller.signal.aborted) setReconciling(false);
      if (request.current === controller) request.current = null;
    }
  }

  return <section className="panel details-panel" aria-labelledby={`${prefix}-title`}>
    <Typography.Title tag="h2" id={`${prefix}-title`} view="small" font="system" defaultMargins={false}>Условия заявки №{application.id}</Typography.Title>
    {counteroffer && <section aria-labelledby={`${prefix}-offer-title`}>
      <Typography.Title tag="h3" id={`${prefix}-offer-title`} view="xsmall" font="system" defaultMargins={false}>Банк предложил другие условия</Typography.Title>
      <Typography.Text tag="p" view="primary-medium" defaultMargins={false}>Предложенная сумма меньше запрошенной. Ознакомьтесь со ставкой и графиком платежей. Подписание договора означает согласие с предложенными условиями.</Typography.Text>
    </section>}
    {manualReview && <Typography.Text tag="p" view="primary-medium" defaultMargins={false}>Условия предварительные; окончательное решение принимает сотрудник банка. Подписание пока недоступно.</Typography.Text>}
    {bankComment && <section aria-labelledby={`${prefix}-comment-title`}>
      <Typography.Title tag="h3" id={`${prefix}-comment-title`} view="xsmall" font="system" defaultMargins={false}>Комментарий банка</Typography.Title>
      <Typography.Text tag="p" view="primary-medium" defaultMargins={false}>{bankComment}</Typography.Text>
    </section>}
    <dl className="details-grid">
      <div><dt>Запрошенная сумма</dt><dd>{formatMoney(application.amount)}</dd></div>
      <div><dt>{manualReview ? 'Предварительная сумма' : counteroffer ? 'Предложенная сумма' : 'Одобренная сумма'}</dt><dd>{application.approvedAmount == null ? 'Не указана' : formatMoney(application.approvedAmount)}</dd></div>
      <div><dt>{manualReview ? 'Предварительная ставка' : 'Ставка'}</dt><dd>{application.approvedRate == null ? 'Не указана' : `${application.approvedRate} % годовых`}</dd></div>
      <div><dt>Срок</dt><dd>{application.termMonths} мес.</dd></div>
    </dl>
    {application.schedule ? <PaymentScheduleView schedule={application.schedule} preliminary={manualReview} />
      : <Typography.Text tag="p" view="primary-small" color="secondary" defaultMargins={false}>{manualReview ? 'График будет доступен после решения банка.' : 'График платежей пока недоступен.'}</Typography.Text>}
    {application.status === 'SIGNED' && <div role="status"><Typography.Text tag="p" view="primary-medium" defaultMargins={false}>Договор подписан. Статус сохранён в истории.</Typography.Text></div>}
    {application.status === 'APPROVED' && <form className="application-form" onSubmit={(event) => void submit(event)} noValidate aria-label="Подписание договора" aria-busy={busy}>
      <div className="form-fields">
        <div>
          <label className="consent-field" htmlFor={`${prefix}-confirmTerms`}>
            <input id={`${prefix}-confirmTerms`} name="confirmTerms" type="checkbox" checked={confirmTerms} required disabled={busy}
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
          }} required block size={56} disabled={busy} autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{4}" maxLength={4}
          hint={<span id={`${prefix}-sms-hint`}>4 цифры из СМС</span>}
          error={errors.smsCode ? <span id={`${prefix}-sms-error`}>{errors.smsCode}</span> : undefined}
          aria-invalid={Boolean(errors.smsCode)} aria-describedby={`${prefix}-sms-hint${errors.smsCode ? ` ${prefix}-sms-error` : ''}`} />
      </div>
      {signingError && <ErrorNotice title="Подписание договора" message={signingError} />}
      {needsReconciliation && !submitting && <Button type="button" view="secondary" size={40} loading={reconciling} disabled={busy} onClick={() => void reconcile()}>Обновить историю</Button>}
      <div className="form-submit"><Button type="submit" view="accent" size={56} loading={submitting} disabled={busy || needsReconciliation || !confirmTerms || !validCode}>Подписать договор</Button></div>
    </form>}
  </section>;
}
