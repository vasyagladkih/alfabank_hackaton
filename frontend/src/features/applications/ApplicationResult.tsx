import { Button } from '@alfalab/core-components-button';
import { Plate } from '@alfalab/core-components-plate';
import { Spinner } from '@alfalab/core-components-spinner';
import type { Application } from '../../api/types';
import type { PollIssue } from './useClientApplications';
import { STATUS_LABELS } from './model';

export function ApplicationResult({ application, issue, retry }: { application: Application; issue?: PollIssue; retry: () => void }) {
  const processing = application.status === 'PROCESSING';
  // A final decision may arrive through history while a failed poll is paused.
  const pollingIssue = processing ? issue : undefined;
  const title = processing ? `Заявка №${application.id} в обработке` : STATUS_LABELS[application.status];
  const view = pollingIssue ? 'attention' : application.status === 'REJECTED' ? 'negative'
    : application.status === 'APPROVED' || application.status === 'SIGNED' ? 'positive' : 'common';
  const messages = {
    PROCESSING: 'Проверяем статус каждые 2 секунды. Можно продолжать работу в кабинете.',
    MANUAL_REVIEW: `Заявка №${application.id} передана сотруднику банка для ручной проверки.`,
    APPROVED: `Заявка №${application.id} одобрена. Решение также сохранено в истории.`,
    SIGNED: `Договор по заявке №${application.id} подписан. Статус сохранён в истории.`,
    REJECTED: application.rejectionReason || 'Причина отказа не указана.',
  };
  const message = pollingIssue
    ? `${pollingIssue.message} ${pollingIssue.retrying ? 'Повторим проверку через 2 секунды.' : 'Автоматическая проверка приостановлена.'}`
    : messages[application.status];

  // Plate supplies its own role="alert"; avoid nested live regions.
  return <div className="result-banner">
    <Plate view={view} title={title} titleView="bold" rounded limitContentWidth={false}
      leftAddons={processing && (!pollingIssue || pollingIssue.retrying) ? <span aria-hidden="true"><Spinner visible preset={24} /></span> : undefined}
      buttons={pollingIssue && !pollingIssue.retrying ? <Button size={40} view="secondary" onClick={retry}>Повторить проверку</Button> : undefined}>
      {message}
    </Plate>
  </div>;
}
