import type { Application, ApplicationStatus, LoanPurpose } from '../../api/types';

export const PURPOSE_LABELS: Record<LoanPurpose, string> = {
  WORKING_CAPITAL: 'Пополнение оборотных средств',
  EQUIPMENT: 'Оборудование и транспорт',
  BUSINESS_EXPANSION: 'Расширение бизнеса',
  REFINANCING: 'Рефинансирование',
};

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  PROCESSING: 'В обработке',
  MANUAL_REVIEW: 'На рассмотрении сотрудника',
  OFFER_PROPOSED: 'Предложены новые условия',
  APPROVED: 'Одобрено',
  REJECTED: 'Отклонено',
};

export const APPLICATION_STATUSES: ApplicationStatus[] = ['PROCESSING', 'MANUAL_REVIEW', 'OFFER_PROPOSED', 'APPROVED', 'REJECTED'];

export const isTerminal = (status: ApplicationStatus) => status === 'APPROVED' || status === 'REJECTED';
export const isAwaitingResolution = (status: ApplicationStatus) => !isTerminal(status);

export function mergeApplications(...groups: Application[][]): Application[] {
  const byId = new Map<number, Application>();
  for (const item of groups.flat()) {
    const previous = byId.get(item.id);
    // Versions order concurrent history, polling and mutation responses. Terminal
    // decisions are immutable, including when a malformed response claims otherwise.
    if (previous && isTerminal(previous.status) && previous.status !== item.status) continue;
    if (previous && item.version < previous.version) continue;
    const previousTime = previous ? Date.parse(previous.updatedAt ?? previous.createdAt) : -Infinity;
    const itemTime = Date.parse(item.updatedAt ?? item.createdAt);
    if (!previous || item.version > previous.version
      || (item.version === previous.version && (itemTime >= previousTime || isTerminal(item.status)))) {
      byId.set(item.id, item);
    }
  }
  return [...byId.values()].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.id - a.id);
}

const money = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', maximumFractionDigits: 0 });
const decimalMoney = new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const date = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
export const formatMoney = (value: number) => money.format(value);
export const formatDecimalMoney = (value: string) => decimalMoney.format(Number(value));
export const formatDate = (value: string) => date.format(new Date(value));
