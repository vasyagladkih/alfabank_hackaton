import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApiClient } from '../../api/client';
import type { ApiClient } from '../../api/client';
import { application, jsonResponse } from '../../test/fixtures';
import { ApplicationResult } from './ApplicationResult';
import { useClientApplications } from './useClientApplications';

function Harness({ api }: { api: ApiClient }) {
  const { applications, pollIssues, refresh, update, retryPolling } = useClientApplications(api);
  return <>
    <button onClick={refresh}>Обновить историю</button>
    <button onClick={() => update({ ...application, id: 43 })}>Добавить другую заявку</button>
    {applications.map((item) => <ApplicationResult key={item.id} application={item} issue={pollIssues[item.id]} retry={() => retryPolling(item.id)} />)}
    <output aria-label="Ошибки опроса">{Object.keys(pollIssues).join(',')}</output>
  </>;
}

describe('client history and polling together', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('clears a paused polling error when refreshed history contains a final decision', async () => {
    let terminal = false;
    vi.stubGlobal('fetch', vi.fn(async (url: string) => url.endsWith('/42')
      ? jsonResponse({ message: 'Заявка временно недоступна' }, 404)
      : jsonResponse([{ ...application, status: terminal ? 'REJECTED' : 'PROCESSING', rejectionReason: 'Долговая нагрузка' }])));
    await act(async () => { render(<Harness api={createApiClient({ token: 'test-token' })} />); });
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(screen.getByLabelText('Ошибки опроса')).toHaveTextContent('42');
    terminal = true;
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Обновить историю' })); });
    expect(screen.getByLabelText('Ошибки опроса')).toBeEmptyDOMElement();
    expect(screen.getByRole('alert')).toHaveTextContent('Долговая нагрузка');
    expect(screen.queryByRole('button', { name: 'Повторить проверку' })).not.toBeInTheDocument();
  });

  it('does not silently restart a paused poll when another application is added', async () => {
    const polls: number[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const id = Number(url.split('/').at(-1));
      if (id) {
        polls.push(id);
        return id === 42 ? jsonResponse({ message: 'Заявка недоступна' }, 404) : jsonResponse({ ...application, id });
      }
      return jsonResponse([application]);
    }));
    await act(async () => { render(<Harness api={createApiClient({ token: 'test-token' })} />); });
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(polls).toEqual([42]);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Добавить другую заявку' })); });
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(polls).toEqual([42, 43]);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Повторить проверку' })); });
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(polls.filter((id) => id === 42)).toHaveLength(2);
  });

  it.each(['MANUAL_REVIEW', 'OFFER_PROPOSED'] as const)('resumes a restored %s application until its final response', async (status) => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => jsonResponse(url.endsWith('/42')
      ? { ...application, status: 'APPROVED', version: 3 }
      : [{ ...application, status, version: 1 }])));
    await act(async () => { render(<Harness api={createApiClient({ token: 'test-token' })} />); });
    await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
    expect(screen.getByRole('alert')).toHaveTextContent('Одобрено');
    expect(screen.getByLabelText('Ошибки опроса')).toBeEmptyDOMElement();
  });
});
