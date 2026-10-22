import { PURPOSE_LABELS } from './model';

export interface ApplicationFields { amount: string; termMonths: string; purpose: string; }
export type ApplicationErrors = Partial<Record<keyof ApplicationFields, string>>;
export function validateApplication(fields: ApplicationFields): ApplicationErrors {
  const errors: ApplicationErrors = {};
  const amount = Number(fields.amount);
  const term = Number(fields.termMonths);
  if (!fields.amount.trim() || !Number.isSafeInteger(amount) || amount < 100000 || amount > 50000000) {
    errors.amount = 'Введите целую сумму от 100 000 до 50 000 000 ₽.';
  }
  if (!fields.termMonths.trim() || !Number.isInteger(term) || term < 1 || term > 60) {
    errors.termMonths = 'Введите целый срок от 1 до 60 месяцев.';
  }
  if (!Object.hasOwn(PURPOSE_LABELS, fields.purpose)) errors.purpose = 'Выберите цель кредита из списка.';
  return errors;
}
