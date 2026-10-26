import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { application } from '../../test/fixtures';
import { ApplicationResult } from './ApplicationResult';

describe('application decision banner', () => {
  it.each([
    ['APPROVED', 'Одобрено'],
    ['REJECTED', 'Отклонено'],
    ['MANUAL_REVIEW', 'На ручной проверке'],
    ['SIGNED', 'Договор подписан'],
  ] as const)('shows %s even if a previous poll failed before history refreshed', (status, label) => {
    render(<ApplicationResult application={{ ...application, status, rejectionReason: 'Превышена долговая нагрузка' }}
      issue={{ message: 'Временная ошибка проверки', retrying: false }} retry={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent(label);
    if (status === 'APPROVED') expect(screen.getByRole('alert')).toHaveTextContent('Комментарий банка: Превышена долговая нагрузка');
    else if (status === 'REJECTED') expect(screen.getByText('Превышена долговая нагрузка')).toBeVisible();
    else expect(screen.queryByText(/Отклонено|Превышена долговая нагрузка/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Временная ошибка проверки/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Повторить проверку' })).not.toBeInTheDocument();
  });

  it('keeps an approved counteroffer positive while including the bank explanation', () => {
    render(<ApplicationResult application={{ ...application, status: 'APPROVED', approvedAmount: 100000,
      rejectionReason: 'Банк предлагает меньшую сумму кредита.' }} retry={vi.fn()} />);
    const banner = screen.getByRole('alert');
    expect(banner).toHaveTextContent('Одобрено');
    expect(banner).toHaveTextContent('Комментарий банка: Банк предлагает меньшую сумму кредита.');
    expect(banner).not.toHaveTextContent('Отклонено');
    expect(banner.className).toMatch(/positive/);
    expect(banner.className).not.toMatch(/negative/);
  });

  it.each([null, '', '   '])('does not label an empty approved comment %j', (rejectionReason) => {
    render(<ApplicationResult application={{ ...application, status: 'APPROVED', rejectionReason }} retry={vi.fn()} />);
    expect(screen.getByRole('alert')).not.toHaveTextContent('Комментарий банка');
  });
});
