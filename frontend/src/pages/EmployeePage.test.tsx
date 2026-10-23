import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { employeeDossier, offeredApplication } from '../test/dossier';
import { deferred, jsonResponse, storeSession } from '../test/fixtures';

function renderEmployee() {
  storeSession('EMPLOYEE');
  return render(<MemoryRouter initialEntries={['/employee']}><App /></MemoryRouter>);
}

const initialDossier = () => structuredClone(employeeDossier);

describe('employee dossier and real decision requests', () => {
  it('opens the dossier and shows score, source evidence and immutable input metadata', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => jsonResponse(url.endsWith('/42') ? employeeDossier : [employeeDossier.application])));
    renderEmployee();
    fireEvent.click(await screen.findByRole('button', { name: 'Открыть досье заявки №42' }));
    const dossier = await screen.findByRole('region', { name: 'Досье заявки №42' });
    await waitFor(() => expect(within(dossier).getByText('580 / 850')).toBeVisible());
    expect(within(dossier).getByText('Учебный балл находится в зоне ручного рассмотрения.')).toBeVisible();
    expect(within(dossier).getByText('Синтетический отчёт БКИ', { exact: true })).toBeVisible();
    expect(within(dossier).getByText(/34,14\s*%/)).toBeVisible();
    expect(within(dossier).getByText('Учебный расчёт завершён.')).toBeVisible();
    expect(within(dossier).getByText(/Демонстрация на синтетических данных/)).toBeVisible();
  });

  it('sends the typed search to the API without changing the status filter', async () => {
    const searches: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      searches.push(url);
      return jsonResponse([employeeDossier.application]);
    }));
    renderEmployee();
    fireEvent.change(await screen.findByLabelText('Поиск по заявкам'), { target: { value: 'Ромашка & 42' } });
    fireEvent.click(screen.getByRole('button', { name: 'Найти' }));
    await waitFor(() => expect(searches.some((url) => new URL(url, 'http://localhost').searchParams.get('search') === 'Ромашка & 42')).toBe(true));
    expect(screen.getByLabelText('Статус заявки')).toHaveValue('ALL');
  });

  it('requires a rationale and accepts only a bounded counteroffer, submitting once with its current version', async () => {
    const posts: Array<{ path: string; body: Record<string, unknown> }> = [];
    const response = deferred<Response>();
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') {
        posts.push({ path: url, body: JSON.parse(String(init.body)) });
        return response.promise;
      }
      return jsonResponse(url.endsWith('/42') ? employeeDossier : [employeeDossier.application]);
    }));
    renderEmployee();
    fireEvent.click(await screen.findByRole('button', { name: 'Открыть досье заявки №42' }));
    fireEvent.change(await screen.findByLabelText('Учебное решение'), { target: { value: 'COUNTEROFFER' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить учебное решение' }));
    expect(await screen.findByLabelText('Основание решения')).toHaveAttribute('aria-invalid', 'true');
    expect(posts).toEqual([]);
    fireEvent.change(screen.getByLabelText('Основание решения'), { target: { value: 'Учебное уменьшение лимита после рассмотрения.' } });
    fireEvent.change(screen.getByLabelText('Предлагаемая сумма, ₽'), { target: { value: '1600000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить учебное решение' }));
    expect(screen.getByLabelText('Предлагаемая сумма, ₽')).toHaveAttribute('aria-invalid', 'true');
    expect(posts).toEqual([]);
    fireEvent.change(screen.getByLabelText('Предлагаемая сумма, ₽'), { target: { value: '900000' } });
    const submit = screen.getByRole('button', { name: 'Сохранить учебное решение' });
    fireEvent.click(submit);
    fireEvent.click(submit);
    await waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0]?.path).toBe('/api/v1/employee/applications/42/decisions');
    expect(posts[0]?.body).toEqual({ expectedVersion: 1, requestId: expect.stringMatching(/^[0-9a-f-]{36}$/), type: 'COUNTEROFFER', rationale: 'Учебное уменьшение лимита после рассмотрения.', offerAmount: 900000 });
    expect(submit).toBeDisabled();
    await act(async () => response.resolve(jsonResponse({ ...employeeDossier, application: offeredApplication, allowedActions: [], audit: [...employeeDossier.audit, { id: 2, eventType: 'EMPLOYEE_COUNTEROFFER', actorRole: 'EMPLOYEE', fromStatus: 'MANUAL_REVIEW', toStatus: 'OFFER_PROPOSED', createdAt: '2026-10-23T12:01:00Z', version: 2, rationale: 'Учебное уменьшение лимита после рассмотрения.' }] })));
    await waitFor(() => expect(screen.getByRole('region', { name: 'Досье заявки №42' })).toHaveTextContent('Предложены новые условия'));
  });

  it('refreshes after a conflict and retains the draft without automatically resubmitting', async () => {
    let detailReads = 0;
    let postCount = 0;
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') { postCount += 1; return jsonResponse({ code: 'CONFLICT', message: 'Заявка изменилась.' }, 409); }
      if (url.endsWith('/42')) {
        detailReads += 1;
        return jsonResponse(detailReads === 1 ? employeeDossier : { ...employeeDossier, application: offeredApplication, allowedActions: [] });
      }
      return jsonResponse([employeeDossier.application]);
    }));
    renderEmployee();
    fireEvent.click(await screen.findByRole('button', { name: 'Открыть досье заявки №42' }));
    const rationale = await screen.findByLabelText('Основание решения');
    fireEvent.change(rationale, { target: { value: 'Сохранённое обоснование сотрудника.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить учебное решение' }));
    await waitFor(() => expect(detailReads).toBe(2));
    expect(rationale).toHaveValue('Сохранённое обоснование сотрудника.');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Сохранить учебное решение' })).toBeDisabled());
    expect(postCount).toBe(1);
    expect(screen.getByText(/Ваш текст сохранён/)).toBeVisible();
  });

  it('reuses an unchanged command after an uncertain response instead of creating a second decision', async () => {
    const commands: Array<Record<string, unknown>> = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') {
        commands.push(JSON.parse(String(init.body)));
        return commands.length === 1 ? jsonResponse({ message: 'Временно недоступно' }, 503) : jsonResponse({ ...employeeDossier, application: { ...employeeDossier.application, status: 'APPROVED', version: 2 }, allowedActions: [] });
      }
      return jsonResponse(url.endsWith('/42') ? employeeDossier : [employeeDossier.application]);
    }));
    renderEmployee();
    fireEvent.click(await screen.findByRole('button', { name: 'Открыть досье заявки №42' }));
    fireEvent.change(await screen.findByLabelText('Основание решения'), { target: { value: 'Учебные данные рассмотрены.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить учебное решение' }));
    await screen.findByText(/Временно недоступно/);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Сохранить учебное решение' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить учебное решение' }));
    await waitFor(() => expect(commands).toHaveLength(2));
    expect(commands[1]).toEqual(commands[0]);
  });

  it.each(['ALL', 'MANUAL_REVIEW'] as const)('keeps a saved decision ahead of a late queue response under %s', async (filter) => {
    const releaseQueue = deferred<void>();
    const approved = { ...employeeDossier, application: { ...employeeDossier.application, status: 'APPROVED', version: 2 }, allowedActions: [] };
    const other = { ...employeeDossier.application, id: 43 };
    let delayQueue = false;
    let pausedReads = 0;
    let saved = false;
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (init.method === 'POST') { saved = true; return jsonResponse(approved); }
      if (url.endsWith('/42')) return jsonResponse(employeeDossier);
      const status = new URL(url, 'http://localhost').searchParams.get('status');
      const snapshot = status === 'MANUAL_REVIEW' ? [other, ...(saved ? [] : [employeeDossier.application])]
        : status === 'APPROVED' && saved ? [approved.application] : [];
      if (delayQueue && !saved) { pausedReads += 1; await releaseQueue.promise; }
      return jsonResponse(snapshot);
    }));
    renderEmployee();
    await screen.findByRole('button', { name: 'Открыть досье заявки №42' });
    if (filter !== 'ALL') {
      fireEvent.change(screen.getByLabelText('Статус заявки'), { target: { value: filter } });
      await screen.findByRole('button', { name: 'Открыть досье заявки №42' });
    }
    fireEvent.click(screen.getByRole('button', { name: 'Открыть досье заявки №42' }));
    fireEvent.change(await screen.findByLabelText('Основание решения'), { target: { value: 'Учебные данные рассмотрены.' } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Обновить', exact: true })).toBeEnabled());
    delayQueue = true;
    fireEvent.click(screen.getByRole('button', { name: 'Обновить', exact: true }));
    await waitFor(() => expect(pausedReads).toBe(filter === 'ALL' ? 5 : 1));
    fireEvent.click(screen.getByRole('button', { name: 'Сохранить учебное решение' }));
    await waitFor(() => expect(screen.getByRole('region', { name: 'Досье заявки №42' })).toHaveTextContent('Версия 2'));
    await act(async () => releaseQueue.resolve());
    await waitFor(() => expect(screen.getByRole('button', { name: 'Обновить', exact: true })).toBeEnabled());
    const table = screen.getByRole('table', { name: 'Реестр кредитных заявок' });
    expect(within(table).getByRole('button', { name: 'Открыть досье заявки №43' })).toBeVisible();
    if (filter === 'ALL') {
      const row = within(table).getByRole('button', { name: 'Открыть досье заявки №42' }).closest('tr')!;
      expect(within(row).getByText('Одобрено', { exact: true })).toBeVisible();
      expect(within(row).queryByText('На рассмотрении сотрудника', { exact: true })).not.toBeInTheDocument();
    } else {
      expect(within(table).queryByRole('button', { name: 'Открыть досье заявки №42' })).not.toBeInTheDocument();
    }
  });

  it('removes an externally decided application when a fresh manual-review query is empty', async () => {
    let moved = false;
    vi.stubGlobal('fetch', vi.fn(async (url: string) => jsonResponse(url.endsWith('/42') ? employeeDossier
      : !moved && new URL(url, 'http://localhost').searchParams.get('status') === 'MANUAL_REVIEW' ? [employeeDossier.application] : [])));
    renderEmployee();
    await screen.findByRole('button', { name: 'Открыть досье заявки №42' });
    fireEvent.change(screen.getByLabelText('Статус заявки'), { target: { value: 'MANUAL_REVIEW' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Открыть досье заявки №42' }));
    await screen.findByLabelText('Основание решения');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Обновить', exact: true })).toBeEnabled());
    moved = true;
    fireEvent.click(screen.getByRole('button', { name: 'Обновить', exact: true }));
    await screen.findByText('Заявки не найдены', { exact: true });
    expect(screen.queryByRole('button', { name: 'Открыть досье заявки №42' })).not.toBeInTheDocument();
  });

  it('shows missing evidence without inventing a score or allowing approval', async () => {
    const dossier = initialDossier();
    dossier.assessment = null;
    dossier.allowedActions = ['REJECT'];
    dossier.sources[0] = { ...dossier.sources[0]!, observedOn: null, freshness: 'MISSING' };
    dossier.facts[0] = { ...dossier.facts[0]!, value: null };
    vi.stubGlobal('fetch', vi.fn(async (url: string) => jsonResponse(url.endsWith('/42') ? dossier : [dossier.application])));
    renderEmployee();
    fireEvent.click(await screen.findByRole('button', { name: 'Открыть досье заявки №42' }));
    await screen.findByText('Оценка ещё не рассчитана');
    expect(screen.getAllByText('Нет данных').length).toBeGreaterThan(0);
    expect(screen.getByRole('option', { name: 'Одобрить исходные условия' })).toBeDisabled();
  });
});
