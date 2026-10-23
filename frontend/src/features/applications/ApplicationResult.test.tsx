import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { application } from '../../test/fixtures';
import { ApplicationResult } from './ApplicationResult';

describe('application decision banner', () => {
  it.each(['APPROVED', 'REJECTED'] as const)('shows %s even if a previous poll failed before history refreshed', (status) => {
    render(<ApplicationResult application={{ ...application, status, rejectionReason: 'Превышена долговая нагрузка' }}
      issue={{ message: 'Временная ошибка проверки', retrying: false }} retry={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent(status === 'APPROVED' ? 'Одобрено' : 'Отклонено');
    if (status === 'REJECTED') expect(screen.getByText('Превышена долговая нагрузка')).toBeVisible();
    expect(screen.queryByText(/Временная ошибка проверки/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Повторить проверку' })).not.toBeInTheDocument();
  });

  it('explains manual review without implying a completed decision or a promised deadline', () => {
    render(<ApplicationResult application={{ ...application, status: 'MANUAL_REVIEW', version: 1 }} retry={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent('На рассмотрении сотрудника');
    expect(screen.getByRole('alert')).toHaveTextContent('Статус обновится автоматически');
    expect(screen.getByRole('alert')).not.toHaveTextContent('одобрена');
    expect(screen.getByRole('alert')).not.toHaveTextContent('Отклонено');
  });

  it('shows proposed terms and makes the unresolved demonstration state explicit', () => {
    render(<ApplicationResult application={{ ...application, status: 'OFFER_PROPOSED', version: 2,
      offer: { amount: 900000, termMonths: 12, annualRate: '18.50', monthlyPayment: '82724.12' } }} retry={vi.fn()} />);
    const banner = screen.getByRole('alert');
    expect(banner).toHaveTextContent('Предложены новые условия');
    expect(banner).toHaveTextContent(/900\s*000/);
    expect(banner).toHaveTextContent('18,50');
    expect(banner).toHaveTextContent(/демонстрац/i);
    expect(banner).not.toHaveTextContent('одобрена');
  });
});
