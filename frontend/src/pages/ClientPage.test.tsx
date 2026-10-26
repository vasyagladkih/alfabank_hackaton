import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthProvider';
import { approvedApplication, deferred, jsonResponse, storeSession } from '../test/fixtures';
import { ClientPage } from './ClientPage';

function renderClient() {
  storeSession();
  return render(<MemoryRouter><AuthProvider><ClientPage /></AuthProvider></MemoryRouter>);
}

function enterSigningFields(code = '0001') {
  fireEvent.click(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' }));
  fireEvent.change(screen.getByRole('textbox', { name: 'Код из СМС' }), { target: { value: code } });
}

describe('client application agreement in history', () => {
  it('opens a restored approved application and updates history after signing', async () => {
    const fetch = vi.fn(async (_url: string, init: RequestInit) => jsonResponse(init.method === 'POST'
      ? { ...approvedApplication, status: 'SIGNED' } : [approvedApplication]));
    vi.stubGlobal('fetch', fetch);
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));

    expect(screen.getByRole('heading', { name: 'Условия заявки №42' })).toBeVisible();
    expect(screen.getByRole('table', { name: 'График платежей' })).toBeVisible();
    enterSigningFields();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));

    await waitFor(() => expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent('Договор подписан.');
    expect(within(screen.getByRole('table', { name: 'История заявок' })).getByText('Договор подписан')).toBeVisible();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('makes an application approved through a history refresh available for signing', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(jsonResponse([{ ...approvedApplication, status: 'MANUAL_REVIEW', approvedAmount: null, approvedRate: null, schedule: undefined }]))
      .mockResolvedValue(jsonResponse([approvedApplication]));
    vi.stubGlobal('fetch', fetch);
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));
    expect(screen.queryByRole('button', { name: 'Подписать договор' })).not.toBeInTheDocument();

    const refresh = screen.getByRole('button', { name: 'Обновить историю' });
    await waitFor(() => expect(refresh).toBeEnabled());
    fireEvent.click(refresh);
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));

    expect(await screen.findByRole('button', { name: 'Подписать договор' })).toBeVisible();
    expect(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' })).not.toBeChecked();
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toHaveValue('');
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('refreshes an uncertain signing result without sending the POST again', async () => {
    let signed = false;
    const fetch = vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') {
        signed = true;
        return jsonResponse({ message: 'Ответ на подписание потерян' }, 503);
      }
      const current = { ...approvedApplication, status: signed ? 'SIGNED' : 'APPROVED' };
      return jsonResponse(url.endsWith('/42') ? current : [current]);
    });
    vi.stubGlobal('fetch', fetch);
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));
    enterSigningFields();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('договор мог быть подписан сервером');
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    const form = screen.getByRole('form', { name: 'Подписание договора' });
    fireEvent.click(within(form).getByRole('button', { name: 'Обновить историю' }));

    await waitFor(() => expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent('Договор подписан.');
    expect(fetch.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1);
    expect(fetch.mock.calls.some(([url]) => url === '/api/v1/client/applications/42')).toBe(true);
  });

  it('keeps an uncertain signature blocked after selecting another application and returning', async () => {
    const history = [approvedApplication, { ...approvedApplication, id: 43 }];
    const fetch = vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') return jsonResponse({ message: 'Результат подписания неизвестен' }, 503);
      return jsonResponse(url.endsWith('/42') ? approvedApplication : history);
    });
    vi.stubGlobal('fetch', fetch);
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));
    enterSigningFields();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('договор мог быть подписан сервером');

    fireEvent.click(screen.getByRole('button', { name: 'Условия заявки №43' }));
    fireEvent.click(screen.getByRole('button', { name: 'Условия заявки №42' }));
    enterSigningFields();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    const form = screen.getByRole('form', { name: 'Подписание договора' });
    fireEvent.click(within(form).getByRole('button', { name: 'Обновить историю' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled());
    expect(fetch.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1);
    expect(fetch.mock.calls.some(([url]) => url === '/api/v1/client/applications/42')).toBe(true);
  });

  it('resets consent and code on selection and ignores signing from the previous selection', async () => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>((_url, init) => init?.method === 'POST' ? pending.promise
      : Promise.resolve(jsonResponse([approvedApplication, { ...approvedApplication, id: 43 }])));
    vi.stubGlobal('fetch', fetch);
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));
    enterSigningFields();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    const signal = fetch.mock.calls.find(([, init]) => init?.method === 'POST')![1]!.signal as AbortSignal;

    fireEvent.click(screen.getByRole('button', { name: 'Условия заявки №43' }));

    expect(signal.aborted).toBe(true);
    expect(screen.getByRole('heading', { name: 'Условия заявки №43' })).toBeVisible();
    expect(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' })).not.toBeChecked();
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toHaveValue('');
    await act(async () => { pending.resolve(jsonResponse({ ...approvedApplication, status: 'SIGNED' })); });
    fireEvent.click(screen.getByRole('button', { name: 'Условия заявки №42' }));
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' })).not.toBeChecked();
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toHaveValue('');
    expect(within(screen.getByRole('table', { name: 'История заявок' })).queryByText('Договор подписан')).not.toBeInTheDocument();
  });

  it.each(['pending', 'uncertain'] as const)('hides prior-session agreements after a %s signature until fresh history succeeds', async (outcome) => {
    const signature = deferred<Response>();
    const replacementHistory = deferred<Response>();
    let reads = 0;
    const fetch = vi.fn<typeof globalThis.fetch>((_url, init) => {
      if (init?.method === 'POST') return outcome === 'pending' ? signature.promise
        : Promise.resolve(jsonResponse({ message: 'Ответ потерян' }, 503));
      reads += 1;
      return reads === 1 ? Promise.resolve(jsonResponse([approvedApplication])) : replacementHistory.promise;
    });
    vi.stubGlobal('fetch', fetch);
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));
    enterSigningFields();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    if (outcome === 'uncertain') await screen.findByRole('alert');
    const signal = fetch.mock.calls.find(([, init]) => init?.method === 'POST')![1]!.signal as AbortSignal;

    act(() => {
      localStorage.setItem('auth_token', 'replacement-client-session');
      window.dispatchEvent(new StorageEvent('storage', { key: 'auth_token' }));
    });
    if (outcome === 'pending') expect(signal.aborted).toBe(true);
    expect(screen.queryByRole('button', { name: 'Условия заявки №42' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument();
    await act(async () => { replacementHistory.reject(new TypeError('offline')); });
    expect(await screen.findByRole('alert')).toHaveTextContent('Не удалось обновить историю');
    await act(async () => { signature.resolve(jsonResponse({ ...approvedApplication, status: 'SIGNED' })); });
    expect(screen.queryByRole('button', { name: 'Условия заявки №42' })).not.toBeInTheDocument();
    expect(fetch.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
  });

  it('clears the selected agreement when the API session changes', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(jsonResponse([approvedApplication]))));
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));
    expect(screen.getByRole('heading', { name: 'Условия заявки №42' })).toBeVisible();

    act(() => {
      localStorage.setItem('auth_token', 'new-client-session-token');
      window.dispatchEvent(new StorageEvent('storage', { key: 'auth_token' }));
    });

    expect(screen.queryByRole('heading', { name: 'Условия заявки №42' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument();
  });
});
