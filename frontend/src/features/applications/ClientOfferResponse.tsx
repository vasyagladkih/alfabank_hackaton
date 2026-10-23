import { useEffect, useRef, useState } from 'react';
import { Button } from '@alfalab/core-components-button';
import { Typography } from '@alfalab/core-components-typography';
import { ApiError, errorMessage, isAbortError } from '../../api/client';
import type { Application, OfferResponseRequest } from '../../api/types';
import { useAuth } from '../../auth/AuthProvider';
import { ErrorNotice } from '../../components/Feedback';
import { formatMoney } from './model';

export function ClientOfferResponse({ application, onUpdated }: { application: Application; onUpdated: (value: Application) => void }) {
  const { api } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const request = useRef<AbortController | null>(null);
  const command = useRef<{ fingerprint: string; body: OfferResponseRequest } | null>(null);
  useEffect(() => () => request.current?.abort(), [api]);

  async function respond(decision: OfferResponseRequest['decision']) {
    if (request.current || application.status !== 'OFFER_PROPOSED' || !application.offer) return;
    const draft = { expectedVersion: application.version, decision };
    const fingerprint = JSON.stringify(draft);
    if (command.current?.fingerprint !== fingerprint) command.current = { fingerprint, body: { ...draft, requestId: crypto.randomUUID() } };
    const controller = new AbortController();
    request.current = controller;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await api.respondToOffer(application.id, command.current.body, controller.signal);
      if (!controller.signal.aborted) onUpdated(updated);
    } catch (cause) {
      if (controller.signal.aborted || isAbortError(cause)) return;
      if (cause instanceof ApiError && cause.status === 409) {
        try {
          const updated = await api.getApplication(application.id, controller.signal);
          if (!controller.signal.aborted) {
            setError('Предложение изменилось. Проверьте актуальные условия и подтвердите выбор ещё раз.');
            onUpdated(updated);
          }
        } catch (refreshError) {
          if (!controller.signal.aborted && !isAbortError(refreshError)) setError('Предложение изменилось. Не удалось обновить условия — обновите историю перед ответом.');
        }
      } else {
        setError(`${errorMessage(cause)}${cause instanceof ApiError && (cause.status === 0 || cause.status >= 500) ? ' Результат ответа не подтверждён. Обновите историю перед повтором.' : ''}`);
      }
    } finally {
      if (!controller.signal.aborted) setSubmitting(false);
      if (request.current === controller) request.current = null;
    }
  }

  return <section className="offer-response panel" aria-labelledby={`offer-${application.id}-title`} aria-busy={submitting}>
    <Typography.Title tag="h2" id={`offer-${application.id}-title`} view="xsmall" font="system" defaultMargins={false}>Ваш ответ на учебное предложение №{application.id}</Typography.Title>
    <p>Вы запрашивали {formatMoney(application.amount)}. {application.offer ? `Предложена сумма ${formatMoney(application.offer.amount)} на ${application.offer.termMonths} мес.` : 'Условия пока недоступны. Обновите историю.'}</p>
    <p className="section-description">Ответ изменит только состояние демо-заявки. Это не подписание договора и не согласие на реальное финансирование.</p>
    {error && <ErrorNotice title="Ответ на предложение" message={error} />}
    {application.offer && <div className="offer-response-actions">
      <Button view="accent" size={48} loading={submitting} disabled={submitting} onClick={() => void respond('ACCEPT')}>Принять учебные условия</Button>
      <Button view="secondary" size={48} disabled={submitting} onClick={() => void respond('REJECT')}>Отказаться от предложения</Button>
    </div>}
  </section>;
}
