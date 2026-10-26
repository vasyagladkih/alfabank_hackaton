import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { paymentSchedule } from '../../test/fixtures';
import { formatMoney } from './model';
import { PaymentScheduleView } from './PaymentScheduleView';

describe('payment schedule', () => {
  it('shows server totals and every payment field without recalculating them', () => {
    render(<PaymentScheduleView schedule={{
      ...paymentSchedule,
      monthlyPayment: 7001,
      totalInterest: 901,
      totalPayout: 99999,
      items: [{ monthNumber: 7, paymentDate: '2027-01-01', totalPayment: 1234,
        principalPayment: 1001, interestPayment: 233, remainingDebt: 45678 }],
    }} />);

    expect(screen.getByText(formatMoney(7001).replace(/\s+/g, ' '))).toBeVisible();
    expect(screen.getByText(formatMoney(901).replace(/\s+/g, ' '))).toBeVisible();
    expect(screen.getByText(formatMoney(99999).replace(/\s+/g, ' '))).toBeVisible();
    const table = screen.getByRole('table', { name: 'График платежей' });
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Месяц', 'Дата платежа', 'Платёж', 'Основной долг', 'Проценты', 'Остаток долга',
    ]);
    expect(within(table).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      '7', '01.01.2027', formatMoney(1234), formatMoney(1001), formatMoney(233), formatMoney(45678),
    ]);
    expect(within(table).getByText('01.01.2027')).toHaveAttribute('dateTime', '2027-01-01');
  });

  it('keeps a date-only payment date unchanged across the year boundary', () => {
    render(<PaymentScheduleView schedule={{
      ...paymentSchedule,
      items: [
        { ...paymentSchedule.items[0]!, monthNumber: 1, paymentDate: '2026-12-31' },
        { ...paymentSchedule.items[0]!, monthNumber: 2, paymentDate: '2027-01-01' },
      ],
    }} />);

    const table = screen.getByRole('table', { name: 'График платежей' });
    expect(within(table).getByText('31.12.2026')).toHaveAttribute('dateTime', '2026-12-31');
    expect(within(table).getByText('01.01.2027')).toHaveAttribute('dateTime', '2027-01-01');
    expect(within(table).getAllByRole('row')).toHaveLength(3);
  });
});
