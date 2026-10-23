import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { offeredApplication } from '../test/dossier';
import { deferred, jsonResponse, storeSession } from '../test/fixtures';

function renderClient() {
  storeSession();
  return render(<MemoryRouter initialEntries={['/client']}><App /></MemoryRouter>);
}

describe('client counteroffer consent', () => {
  it.each([
    ['Принять учебные условия', 'ACCEPT', 'APPROVED', 'Одобрено'],
    ['Отказаться от предложения', 'REJECT', 'REJECTED', 'Отклонено'],
  ] as const)('sends %s once and retains the final banner', async (label, decision, status, finalLabel) => {
    const commands: Array<Record<string, unknown>> = [];
    const response = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') {
        expect(url).toBe('/api/v1/client/applications/42/offer-response');
        commands.push(JSON.parse(String(init.body)));
        return response.promise;
      }
      return jsonResponse(url.endsWith('/42') ? offeredApplication : [offeredApplication]);
    }));
    renderClient();
    const button = await screen.findByRole('button', { name: label });
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() => expect(commands).toHaveLength(1));
    expect(commands[0]).toEqual({ expectedVersion: 2, requestId: expect.stringMatching(/^[0-9a-f-]{36}$/), decision });
    expect(button).toBeDisabled();
    await act(async () => response.resolve(jsonResponse({ ...offeredApplication, status, version: 3, rejectionReason: status === 'REJECTED' ? 'Клиент отклонил учебное предложение.' : null })));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(finalLabel));
    expect(screen.queryByRole('button', { name: 'Принять учебные условия' })).not.toBeInTheDocument();
  });

  it('loads changed terms after a conflict and requires another explicit response', async () => {
    let postCount = 0;
    const revised = { ...offeredApplication, version: 3, offer: { ...offeredApplication.offer!, amount: 800000 } };
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') { postCount += 1; return jsonResponse({ code: 'CONFLICT', message: 'Предложение изменилось.' }, 409); }
      return jsonResponse(url.endsWith('/42') ? revised : [offeredApplication]);
    }));
    renderClient();
    fireEvent.click(await screen.findByRole('button', { name: 'Принять учебные условия' }));
    await screen.findByText(/Проверьте актуальные условия/);
    expect(screen.getAllByText(/800\s*000/).length).toBeGreaterThan(0);
    expect(postCount).toBe(1);
  });
});
