import type { Application, ApplicationStatus, LoanPurpose } from '../../api/types';

export const PURPOSE_LABELS: Record<LoanPurpose, string> = {
  WORKING_CAPITAL: 'Пополнение оборотных средств',
  EQUIPMENT: 'Оборудование и транспорт',
  BUSINESS_EXPANSION: 'Расширение бизнеса',
  REFINANCING: 'Рефинансирование',
};

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  PROCESSING: 'В обработке', MANUAL_REVIEW: 'На ручной проверке', APPROVED: 'Одобрено', SIGNED: 'Договор подписан', REJECTED: 'Отклонено',
};

export const APPLICATION_STATUSES: ApplicationStatus[] = ['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED', 'REJECTED'];

function compareTimestamps(left: string, right: string): number {
  const milliseconds = Date.parse(left) - Date.parse(right);
  if (milliseconds !== 0) return milliseconds;

  // Date.parse normalizes timezone offsets but drops PostgreSQL's sub-millisecond precision.
  const remainingFraction = (value: string) => (value.match(/\.(\d+)(?:Z|[+-]\d{2}:?\d{2})?$/i)?.[1] ?? '').slice(3);
  const leftFraction = remainingFraction(left);
  const rightFraction = remainingFraction(right);
  const precision = Math.max(leftFraction.length, rightFraction.length);
  const normalizedLeft = leftFraction.padEnd(precision, '0');
  const normalizedRight = rightFraction.padEnd(precision, '0');
  return normalizedLeft === normalizedRight ? 0 : normalizedLeft > normalizedRight ? 1 : -1;
}

export function mergeApplications(...groups: Application[][]): Application[] {
  const byId = new Map<number, Application>();
  for (const item of groups.flat()) {
    const previous = byId.get(item.id);
    if (previous) {
      // An omitted update time cannot prove that a forward transition is stale.
      const order = previous.updatedAt && item.updatedAt
        ? compareTimestamps(item.updatedAt, previous.updatedAt)
        : compareTimestamps(item.createdAt, previous.createdAt);
      if (order < 0) continue;
      if (previous.status === 'APPROVED' && item.status !== 'APPROVED' && item.status !== 'SIGNED') continue;
      if ((previous.status === 'SIGNED' || previous.status === 'REJECTED') && item.status !== previous.status) continue;
      // Reopening manual review requires a provably newer retry, not an ambiguous history response.
      if (previous.status === 'MANUAL_REVIEW' && item.status === 'PROCESSING'
        && (!previous.updatedAt || !item.updatedAt || order === 0)) continue;
    }
    byId.set(item.id, item);
  }
  return [...byId.values()].sort((a, b) => compareTimestamps(b.createdAt, a.createdAt) || b.id - a.id);
}

const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 });
const date = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
export const formatMoney = (value: number) => money.format(value);
export const formatDate = (value: string) => date.format(new Date(value));
