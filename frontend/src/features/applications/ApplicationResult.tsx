import { Button } from '@alfalab/core-components-button';
import { Plate } from '@alfalab/core-components-plate';
import { Spinner } from '@alfalab/core-components-spinner';
import type { Application } from '../../api/types';
import type { PollIssue } from './useClientApplications';
import { formatDecimalMoney, formatMoney, isAwaitingResolution, STATUS_LABELS } from './model';

export function ApplicationResult({ application, issue, retry }: { application: Application; issue?: PollIssue; retry: () => void }) {
  const processing = application.status === 'PROCESSING';
  // A final decision may arrive through history while a failed poll is paused.
  const pollingIssue = isAwaitingResolution(application.status) ? issue : undefined;
  const title = processing ? `Заявка №${application.id} в обработке` : STATUS_LABELS[application.status];
  const view = pollingIssue || application.status === 'MANUAL_REVIEW' ? 'attention'
    : application.status === 'APPROVED' ? 'positive' : application.status === 'REJECTED' ? 'negative' : 'common';
  const messages = {
    PROCESSING: 'Проверяем статус каждые 2 секунды. Можно продолжать работу в кабинете.',
    MANUAL_REVIEW: `Заявка №${application.id} ожидает учебного рассмотрения сотрудником. Статус обновится автоматически.`,
    OFFER_PROPOSED: `Для заявки №${application.id} предложены новые условия. Для завершения требуется ответ клиента.`,
    APPROVED: `Заявка №${application.id} одобрена. Решение также сохранено в истории.`,
    REJECTED: application.rejectionReason || 'Причина отказа не указана.',
  };
  const offer = application.offer;

  // Plate supplies its own role="alert"; avoid nested live regions.
  return <div className="result-banner">
    <Plate view={view} title={title} titleView="bold" rounded limitContentWidth={false}
      leftAddons={processing && (!pollingIssue || pollingIssue.retrying) ? <span aria-hidden="true"><Spinner visible preset={24} /></span> : undefined}
      buttons={pollingIssue && !pollingIssue.retrying ? <Button size={40} view="secondary" onClick={retry}>Повторить проверку</Button> : undefined}>
      <div>{messages[application.status]}</div>
      {application.decisionSummary && <p>{application.decisionSummary.explanation}</p>}
      {offer && <p>Сумма: {formatMoney(offer.amount)} · Срок: {offer.termMonths} мес. · Ставка: {offer.annualRate.replace('.', ',')}% годовых · Платёж: {formatDecimalMoney(offer.monthlyPayment)} в месяц.</p>}
      {pollingIssue && <p>{pollingIssue.message} {pollingIssue.retrying ? 'Повторим проверку через 2 секунды.' : 'Автоматическая проверка приостановлена.'}</p>}
      {!processing && <p>Демонстрация на синтетических данных. Решение не создаёт обязательств и не означает выдачу денег.</p>}
    </Plate>
  </div>;
}
