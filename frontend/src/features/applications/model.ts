import type { Application, ApplicationStatus, LoanPurpose } from '../../api/types';

export const PURPOSE_LABELS: Record<LoanPurpose, string> = {
  WORKING_CAPITAL: 'Пополнение оборотных средств',
  EQUIPMENT: 'Оборудование и транспорт',
  BUSINESS_EXPANSION: 'Расширение бизнеса',
  REFINANCING: 'Рефинансирование',
};

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  PROCESSING: 'В обработке', APPROVED: 'Одобрено', REJECTED: 'Отклонено',
};

export const APPLICATION_STATUSES: ApplicationStatus[] = ['PROCESSING', 'APPROVED', 'REJECTED'];

export function mergeApplications(...groups: Application[][]): Application[] {
  const byId = new Map<number, Application>();
  for (const item of groups.flat()) {
    const previous = byId.get(item.id);
    // Decisions are terminal. A slow history/poll response must not undo one.
    if (previous && previous.status !== 'PROCESSING' && item.status === 'PROCESSING') continue;
    const previousTime = previous ? Date.parse(previous.updatedAt ?? previous.createdAt) : -Infinity;
    const itemTime = Date.parse(item.updatedAt ?? item.createdAt);
    if (!previous || itemTime >= previousTime || (previous.status === 'PROCESSING' && item.status !== 'PROCESSING')) {
      byId.set(item.id, item);
    }
  }
  return [...byId.values()].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.id - a.id);
}

const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 });
const date = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
export const formatMoney = (value: number) => money.format(value);
export const formatDate = (value: string) => date.format(new Date(value));
