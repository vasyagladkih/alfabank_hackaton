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

function enterSigningFields(code = 'abc-9') {
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
    const fetch = vi.fn(async (_url: string, init: RequestInit) => {
      if (init.method === 'POST') {
        signed = true;
        return jsonResponse({ message: 'Ответ на подписание потерян' }, 503);
      }
      return jsonResponse([{ ...approvedApplication, status: signed ? 'SIGNED' : 'APPROVED' }]);
    });
    vi.stubGlobal('fetch', fetch);
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Условия заявки №42' }));
    enterSigningFields();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('договор мог быть подписан сервером');
    const form = screen.getByRole('form', { name: 'Подписание договора' });
    fireEvent.click(within(form).getByRole('button', { name: 'Обновить историю' }));

    await waitFor(() => expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument());
    expect(screen.getByRole('status')).toHaveTextContent('Договор подписан.');
    expect(fetch.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1);
    expect(fetch).toHaveBeenCalledTimes(3);
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
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled();
    expect(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' })).not.toBeChecked();
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toHaveValue('');
    expect(within(screen.getByRole('table', { name: 'История заявок' })).queryByText('Договор подписан')).not.toBeInTheDocument();
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
