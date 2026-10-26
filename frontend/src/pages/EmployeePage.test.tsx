import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthProvider';
import { application, applicationPage, deferred, jsonResponse, storeSession } from '../test/fixtures';
import { EmployeePage } from './EmployeePage';

function renderRegistry() {
  storeSession('EMPLOYEE');
  return render(<MemoryRouter><AuthProvider><EmployeePage /></AuthProvider></MemoryRouter>);
}

describe('employee registry pages', () => {
  it('uses server totals and order, navigates pages and refreshes the current page', async () => {
    const fetch = vi.fn(async (url: string) => {
      const page = Number(new URL(url, 'http://localhost').searchParams.get('page'));
      return jsonResponse({ ...applicationPage, page, totalElements: 21, totalPages: 3,
        items: page === 0 ? [application, { ...application, id: 43, createdAt: '2026-10-23T14:30:00Z' }] : [{ ...application, id: 44 }] });
    });
    vi.stubGlobal('fetch', fetch);
    renderRegistry();
    expect(await screen.findByText('Найдено заявок: 21')).toBeVisible();
    expect(screen.getByText('Страница 1 из 3')).toBeVisible();
    const rows = within(screen.getByRole('table')).getAllByRole('row');
    expect(rows.slice(1).map((row) => within(row).getAllByRole('cell')[0]?.textContent)).toEqual(['№42', '№43']);
    expect(screen.getByRole('button', { name: 'Назад' })).toBeDisabled();
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(fetch.mock.calls[0]![0]).toBe('/api/v1/employee/applications?page=0&size=10');

    fireEvent.click(screen.getByRole('button', { name: 'Далее' }));
    expect(await screen.findByText('Страница 2 из 3')).toBeVisible();
    expect(screen.getByText('№44')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Обновить' }));
    await screen.findByText('Страница 2 из 3');
    expect(fetch.mock.calls.at(-1)![0]).toBe('/api/v1/employee/applications?page=1&size=10');
    fireEvent.click(screen.getByRole('button', { name: 'Назад' }));
    expect(await screen.findByText('Страница 1 из 3')).toBeVisible();
  });

  it.each(['MANUAL_REVIEW', 'SIGNED'] as const)('resets the page when filtering by %s', async (status) => {
    const fetch = vi.fn(async (url: string) => {
      const query = new URL(url, 'http://localhost').searchParams;
      return jsonResponse({ ...applicationPage, page: Number(query.get('page')), totalElements: 11, totalPages: 2 });
    });
    vi.stubGlobal('fetch', fetch);
    renderRegistry();
    await screen.findByText('Страница 1 из 2');
    fireEvent.click(screen.getByRole('button', { name: 'Далее' }));
    await screen.findByText('Страница 2 из 2');
    expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
    fireEvent.change(screen.getByRole('combobox', { name: 'Статус заявки' }), { target: { value: status } });
    await screen.findByText('Страница 1 из 2');
    expect(fetch.mock.calls.at(-1)![0]).toBe(`/api/v1/employee/applications?page=0&size=10&status=${status}`);
    expect(screen.getAllByRole('option').map((option) => option.getAttribute('value'))).toEqual(['ALL', 'PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED', 'REJECTED']);
  });

  it('shows an empty page and disables both navigation buttons', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...applicationPage, items: [], totalElements: 0, totalPages: 0 })));
    renderRegistry();
    expect(await screen.findByText('Заявок с таким статусом пока нет')).toBeVisible();
    expect(screen.getByText('Найдено заявок: 0')).toBeVisible();
    expect(screen.getByText('Страница 0 из 0')).toBeVisible();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Назад' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
  });

  it('aborts the previous filter request and ignores its late response', async () => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>().mockReturnValueOnce(pending.promise)
      .mockResolvedValue(jsonResponse({ ...applicationPage, items: [{ ...application, id: 43, status: 'SIGNED' }] }));
    vi.stubGlobal('fetch', fetch);
    renderRegistry();
    const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
    fireEvent.change(screen.getByRole('combobox', { name: 'Статус заявки' }), { target: { value: 'SIGNED' } });
    expect(await screen.findByText('№43')).toBeVisible();
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(jsonResponse(applicationPage)); });
    expect(screen.queryByText('№42')).not.toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Договор подписан')).toBeVisible();
  });

  it('aborts an in-flight request on unmount', async () => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>(() => pending.promise);
    vi.stubGlobal('fetch', fetch);
    const { unmount } = renderRegistry();
    await waitFor(() => expect(fetch).toHaveBeenCalledOnce());
    const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
    unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(jsonResponse(applicationPage)); });
  });
});
