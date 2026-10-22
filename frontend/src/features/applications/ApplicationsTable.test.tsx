import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
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
});
