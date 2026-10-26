import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import type { Application } from '../../api/types';
import { AuthProvider } from '../../auth/AuthProvider';
import { approvedApplication, deferred, jsonResponse, storeSession } from '../../test/fixtures';
import { formatMoney } from './model';
import { ApplicationAgreement } from './ApplicationAgreement';

function renderAgreement(application: Application = approvedApplication) {
  storeSession();
  const onUpdated = vi.fn();
  const onRefresh = vi.fn();
  return { ...render(<MemoryRouter><AuthProvider><ApplicationAgreement application={application} onUpdated={onUpdated} onRefresh={onRefresh} /></AuthProvider></MemoryRouter>), onUpdated, onRefresh };
}

function fillAgreement(code = ' custom-code 9 ') {
  fireEvent.click(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' }));
  fireEvent.change(screen.getByRole('textbox', { name: 'Код из СМС' }), { target: { value: code } });
}

describe('client agreement', () => {
  it('requires consent and a nonblank code, then sends the supplied code unchanged', async () => {
    const signed = { ...approvedApplication, status: 'SIGNED' };
    const fetch = vi.fn().mockResolvedValue(jsonResponse(signed));
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderAgreement();

    expect(screen.getByText('Сумма кредита').nextElementSibling).toHaveTextContent(formatMoney(approvedApplication.approvedAmount!).replace(/\s+/g, ' '));
    expect(screen.getByText(`${approvedApplication.approvedRate} % годовых`)).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    expect(screen.getByText('Подтвердите согласие с условиями договора.')).toBeVisible();
    expect(screen.getByText('Введите код из СМС.')).toBeVisible();
    expect(screen.getByRole('checkbox')).toHaveFocus();
    expect(fetch).not.toHaveBeenCalled();

    fillAgreement('   ');
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toHaveFocus();
    expect(fetch).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox', { name: 'Код из СМС' }), { target: { value: ' custom-code 9 ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(signed));
    expect(fetch).toHaveBeenCalledOnce();
    expect(fetch.mock.calls[0]![0]).toBe('/api/v1/client/applications/42/sign');
    expect(fetch.mock.calls[0]![1]).toMatchObject({ method: 'POST', body: JSON.stringify({ confirmTerms: true, smsCode: ' custom-code 9 ' }) });
  });

  it('blocks duplicate submissions while signing is in flight', async () => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>(() => pending.promise);
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderAgreement();
    fillAgreement();
    const form = screen.getByRole('form', { name: 'Подписание договора' });
    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(fetch).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    expect(screen.getByRole('checkbox')).toBeDisabled();
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toBeDisabled();
    await act(async () => { pending.resolve(jsonResponse({ ...approvedApplication, status: 'SIGNED' })); });
    expect(onUpdated).toHaveBeenCalledOnce();
  });

  it('shows a server validation error without repeating the POST', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse({ message: 'Неверный код подтверждения' }, 400));
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderAgreement();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Неверный код подтверждения');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled());
    expect(screen.queryByRole('button', { name: 'Обновить историю' })).not.toBeInTheDocument();
    expect(onUpdated).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledOnce();
  });

  it.each([0, 503])('offers a manual history refresh after an uncertain %s error', async (status) => {
    const fetch = status === 0 ? vi.fn().mockRejectedValue(new TypeError('Network error'))
      : vi.fn().mockResolvedValue(jsonResponse({ message: 'Подписание временно недоступно' }, status));
    vi.stubGlobal('fetch', fetch);
    const { onRefresh } = renderAgreement();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Перед повторной отправкой обновите историю: договор мог быть подписан сервером.');
    fireEvent.click(screen.getByRole('button', { name: 'Обновить историю' }));
    expect(onRefresh).toHaveBeenCalledOnce();
    expect(fetch).toHaveBeenCalledOnce();
  });

  it.each(['unmount', 'api change'])('aborts signing on %s and ignores a late response', async (transition) => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>(() => pending.promise);
    vi.stubGlobal('fetch', fetch);
    const { onUpdated, unmount } = renderAgreement();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;

    if (transition === 'unmount') unmount();
    else act(() => {
      localStorage.setItem('auth_token', 'another-session-token');
      window.dispatchEvent(new StorageEvent('storage', { key: 'auth_token' }));
    });
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(jsonResponse({ ...approvedApplication, status: 'SIGNED' })); });
    expect(onUpdated).not.toHaveBeenCalled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it.each(['PROCESSING', 'MANUAL_REVIEW', 'SIGNED', 'REJECTED'] as const)('does not offer signing for %s', (status) => {
    renderAgreement({ ...approvedApplication, status });
    expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument();
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Код из СМС' })).not.toBeInTheDocument();
    if (status === 'SIGNED') expect(screen.getByRole('status')).toHaveTextContent('Договор подписан.');
  });

  it('handles an absent optional schedule without an error', () => {
    renderAgreement({ ...approvedApplication, approvedAmount: undefined, approvedRate: undefined, schedule: undefined });
    const panel = screen.getByRole('region', { name: 'Условия заявки №42' });
    expect(within(panel).getByText('График платежей пока недоступен.')).toBeVisible();
    expect(within(panel).getAllByText('Не указана')).toHaveLength(2);
    expect(within(panel).queryByRole('table')).not.toBeInTheDocument();
    expect(within(panel).queryByRole('alert')).not.toBeInTheDocument();
  });
});
