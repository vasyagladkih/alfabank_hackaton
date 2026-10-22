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
});
