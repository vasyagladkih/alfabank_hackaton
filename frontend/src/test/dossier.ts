import type { components } from '../api/schema';
import { application } from './fixtures';

export const employeeDossier: components['schemas']['EmployeeDossier'] = {
  application: { ...application, status: 'MANUAL_REVIEW', version: 1 },
  demoOnly: true,
  snapshotCapturedAt: '2026-10-23T12:00:00Z',
  snapshotAsOf: '2026-10-23',
  snapshotOrigin: 'SYNTHETIC_DEMO',
  snapshotSha256: 'a'.repeat(64),
  facts: [
    { code: 'ACCOUNT_AGE', label: 'Срок обслуживания', value: '14', unit: 'мес.', source: 'PROFILE' },
    { code: 'existingDebtPaymentRub', label: 'Совокупный платёж по существующим долгам', value: '100000.00', unit: '₽/мес.', source: 'INTERNAL + BKI' },
  ],
  cashflows: ['2026-04', '2026-05', '2026-06', '2026-07', '2026-08', '2026-09'].map((month) => ({ month, turnover: '2000000.00' })),
  sources: [
    { code: 'PROFILE', label: 'Синтетический профиль', version: 'demo-v1', observedOn: '2026-10-23', demoOnly: true, freshness: 'FRESH' },
    { code: 'INTERNAL', label: 'Синтетические внутренние данные', version: 'demo-v1', observedOn: '2026-10-23', demoOnly: true, freshness: 'FRESH' },
    { code: 'BKI', label: 'Синтетический отчёт БКИ', version: 'demo-v1', observedOn: '2026-10-23', demoOnly: true, freshness: 'FRESH' },
  ],
  assessment: {
    ruleVersion: 'demo-v1', asOf: '2026-10-23', outcome: 'MANUAL_REVIEW', score: 580, maxScore: 850,
    evidenceComplete: true, eligibleForManualApproval: true,
    annualRate: '18.50', monthlyIncome: '700000.00', existingDebtPayment: '100000.00', requestedPayment: '139000.00', debtRatio: '0.34142857', affordableAmount: 1500000,
    components: [
      { code: 'ACCOUNT_AGE', label: 'Срок обслуживания', value: '14 мес.', points: 100, maximum: 200, explanation: 'Учебный вклад срока обслуживания.' },
      { code: 'TURNOVER', label: 'Средний оборот', value: '2000000.00 ₽', points: 180, maximum: 250, explanation: 'Учебный вклад оборота за шесть месяцев.' },
      { code: 'HISTORY', label: 'Кредитная история', value: 'Без текущей просрочки', points: 200, maximum: 250, explanation: 'Синтетический отчёт БКИ.' },
      { code: 'STABILITY', label: 'Стабильность поступлений', value: 'Стабильно', points: 100, maximum: 150, explanation: 'Учебный вклад стабильности.' },
    ],
    findings: [{ code: 'MANUAL_SCORE', severity: 'WARNING', message: 'Учебный балл находится в зоне ручного рассмотрения.' }],
  },
  allowedActions: ['APPROVE', 'REJECT', 'COUNTEROFFER'],
  audit: [{ id: 1, eventType: 'SCORING_COMPLETED', actorRole: 'SYSTEM', fromStatus: 'PROCESSING', toStatus: 'MANUAL_REVIEW', createdAt: '2026-10-23T12:00:00Z', version: 1, rationale: 'Учебный расчёт завершён.' }],
};

export const offeredApplication: components['schemas']['ApplicationResponse'] = {
  ...application, status: 'OFFER_PROPOSED', version: 2,
  offer: { amount: 900000, termMonths: 12, annualRate: '18.50', monthlyPayment: '82724.12' },
};
