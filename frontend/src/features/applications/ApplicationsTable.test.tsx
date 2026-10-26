import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { application } from '../../test/fixtures';
import { ApplicationsTable } from './ApplicationsTable';

describe('Core application tables', () => {
  it.each([false, true])('renders a populated table with employee=%s', (employee) => {
    render(<ApplicationsTable applications={[application]} employee={employee} />);
    const table = screen.getByRole('table');
    expect(within(table).getAllByRole('columnheader')).toHaveLength(employee ? 7 : 6);
    expect(within(table).getAllByRole('cell')).toHaveLength(employee ? 7 : 6);
    expect(within(table).getByText('№42')).toBeVisible();
  });
  it.each([
    ['MANUAL_REVIEW', 'На ручной проверке'],
    ['SIGNED', 'Договор подписан'],
  ] as const)('shows the %s status without a rejection reason', (status, label) => {
    render(<ApplicationsTable applications={[{ ...application, status, rejectionReason: 'Не должна отображаться' }]} employee />);
    expect(screen.getByText(label)).toBeVisible();
    expect(screen.queryByText('Не должна отображаться')).not.toBeInTheDocument();
    expect(screen.queryByText('Отклонено')).not.toBeInTheDocument();
  });
  it.each([false, true])('opens the selected application with employee=%s', (employee) => {
    const onSelect = vi.fn();
    render(<ApplicationsTable applications={[application]} employee={employee} onSelect={onSelect} />);
    fireEvent.click(screen.getByRole('button', { name: `${employee ? 'Досье' : 'Условия'} заявки №42` }));
    expect(onSelect).toHaveBeenCalledExactlyOnceWith(application);
  });
});
