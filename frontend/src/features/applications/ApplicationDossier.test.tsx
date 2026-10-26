import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { ApplicationDetails } from '../../api/types';
import { AuthProvider } from '../../auth/AuthProvider';
import { applicationDetails, approvedApplication, deferred, jsonResponse, storeSession } from '../../test/fixtures';
import { ApplicationDossier } from './ApplicationDossier';

const technicalDetails: ApplicationDetails = {
  ...applicationDetails, status: 'MANUAL_REVIEW', reviewReasonCode: 'TECH_FAILURE', canRetry: true,
  rejectionReason: 'ФНС временно недоступна', schedule: undefined,
};

function wrapper({ children }: { children: ReactNode }) {
  return <MemoryRouter><AuthProvider>{children}</AuthProvider></MemoryRouter>;
}

function renderDossier(onUpdated = vi.fn()) {
  storeSession('EMPLOYEE');
  return { ...render(<ApplicationDossier applicationId={42} onUpdated={onUpdated} />, { wrapper }), onUpdated };
}

function metric(label: string) {
  return screen.getByText(label, { selector: 'dt' }).parentElement!;
}

describe('employee application dossier', () => {
  it('shows the selected application, checks and optional payment schedule', async () => {
    const fetch = vi.fn().mockResolvedValue(jsonResponse(applicationDetails));
    vi.stubGlobal('fetch', fetch);
    renderDossier();
    expect(await screen.findByText('ACTIVE')).toBeVisible();
    expect(screen.getByRole('region', { name: 'Досье заявки №42' })).toBeVisible();
    expect(within(metric('Заёмщик')).getByText('ООО «Ромашка»')).toBeVisible();
    expect(within(metric('Статус')).getByText('Одобрено')).toBeVisible();
    expect(within(metric('Возраст бизнеса, мес.')).getByText('36')).toBeVisible();
    expect(within(metric('Балл БКИ')).getByText('750')).toBeVisible();
    expect(within(metric('Категория риска')).getByText('CURRENT (0 DPD)')).toBeVisible();
    expect(within(metric('Скоринговый балл')).getByText('85')).toBeVisible();
    expect(within(metric('ПДН')).getByText('34.2%')).toBeVisible();
    expect(screen.getByRole('heading', { name: 'График платежей' })).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Перезапустить проверку' })).not.toBeInTheDocument();
    expect(fetch.mock.calls[0]![0]).toBe('/api/v1/employee/applications/42');
  });

  it('shows absent snapshots as missing data and omits an absent schedule', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      ...applicationDetails, companyName: undefined, inn: undefined, user: undefined,
      fns: null, bki: null, scoring: null, schedule: null,
    })));
    renderDossier();
    expect(await screen.findAllByText('Нет данных')).toHaveLength(3);
    expect(within(metric('Заёмщик')).getByText('—')).toBeVisible();
    expect(within(metric('ИНН')).getByText('—')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'График платежей' })).not.toBeInTheDocument();
  });

  it('preserves zero and false while marking absent scalar values', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      ...applicationDetails, schedule: undefined,
      fns: { businessAgeMonths: 0, taxBlocksExist: false },
      bki: { bkiScore: 0, monthlyPaymentRub: 0, currentOverdueDays: 0, cntOverdueOver90d: 0 },
      scoring: { scoringScore: 0, calculatedPdnPercent: 0, maxLimitRub: 0 },
    })));
    renderDossier();
    expect(await screen.findByText('Нет')).toBeVisible();
    expect(within(metric('Статус в ФНС')).getByText('—')).toBeVisible();
    expect(within(metric('Возраст бизнеса, мес.')).getByText('0')).toBeVisible();
    expect(within(metric('Балл БКИ')).getByText('0')).toBeVisible();
    expect(within(metric('Текущая просрочка, дней')).getByText('0')).toBeVisible();
    expect(within(metric('Просрочки свыше 90 дней')).getByText('0')).toBeVisible();
    expect(within(metric('Категория риска')).getByText('—')).toBeVisible();
    expect(within(metric('Скоринговый балл')).getByText('0')).toBeVisible();
    expect(within(metric('ПДН')).getByText('0%')).toBeVisible();
    expect(within(metric('Максимальный лимит')).getByText(/0\s*₽/)).toBeVisible();
    expect(within(metric('Текущие платежи в месяц')).getByText(/0\s*₽/)).toBeVisible();
  });

  it.each([
    ['MANUAL_REVIEW', 'TECH_FAILURE', true, true, true],
    ['MANUAL_REVIEW', 'TECH_FAILURE', false, true, false],
    ['MANUAL_REVIEW', 'BORDERLINE_SCORE', true, false, false],
    ['PROCESSING', 'TECH_FAILURE', true, false, false],
  ] as const)('gates the technical banner and retry for %s/%s/%s', async (status, reviewReasonCode, canRetry, banner, retry) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...technicalDetails, status, reviewReasonCode, canRetry })));
    renderDossier();
    await screen.findByText('ACTIVE');
    expect(Boolean(screen.queryByText('Технический сбой проверки'))).toBe(banner);
    expect(Boolean(screen.queryByRole('button', { name: 'Перезапустить проверку' }))).toBe(retry);
    if (banner) expect(screen.getByRole('alert')).toHaveTextContent('ФНС временно недоступна');
  });

  it('blocks duplicate retries, then reloads the synchronous final decision and notifies the registry', async () => {
    const pending = deferred<Response>();
    let reads = 0;
    const fetch = vi.fn<typeof globalThis.fetch>((_url, options) => {
      if (options?.method === 'POST') return pending.promise;
      return Promise.resolve(jsonResponse(reads++ === 0 ? technicalDetails : applicationDetails));
    });
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderDossier();
    const retry = await screen.findByRole('button', { name: 'Перезапустить проверку' });
    fireEvent.click(retry);
    fireEvent.click(retry);
    expect(retry).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Обновить досье' })).toBeDisabled();
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
    expect(fetch.mock.calls.at(-1)![0]).toBe('/api/v1/employee/applications/42/retry');
    await act(async () => { pending.resolve(jsonResponse(approvedApplication)); });
    expect(await screen.findByText('Одобрено')).toBeVisible();
    expect(onUpdated).toHaveBeenCalledOnce();
    expect(onUpdated).toHaveBeenCalledWith(approvedApplication);
    expect(reads).toBe(2);
    expect(screen.queryByText('В обработке')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Перезапустить проверку' })).not.toBeInTheDocument();
  });

  it('shows a retry error without repeating the POST and permits a manual dossier read', async () => {
    const fetch = vi.fn<typeof globalThis.fetch>((_url, options) => Promise.resolve(options?.method === 'POST'
      ? jsonResponse({ message: 'Повторный запуск завершился ошибкой' }, 500)
      : jsonResponse(technicalDetails)));
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderDossier();
    fireEvent.click(await screen.findByRole('button', { name: 'Перезапустить проверку' }));
    expect(await screen.findByText(/Повторный запуск завершился ошибкой/)).toBeVisible();
    expect(onUpdated).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('button', { name: 'Перезапустить проверку' })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Обновить досье' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Обновить досье' }));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Перезапустить проверку' })).toBeEnabled());
    expect(fetch.mock.calls.at(-1)![1]!.method).toBe('GET');
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
  });

  it.each(['network', 'server', 'invalid response'] as const)('blocks another POST after an ambiguous %s result until a dossier GET succeeds', async (failure) => {
    let reads = 0;
    const fetch = vi.fn<typeof globalThis.fetch>((_url, options) => {
      if (options?.method === 'POST') {
        if (failure === 'network') return Promise.reject(new TypeError('Network failure'));
        return Promise.resolve(failure === 'server'
          ? jsonResponse({ message: 'Проверка временно недоступна' }, 503)
          : jsonResponse({ unexpected: true }));
      }
      reads += 1;
      return Promise.resolve(reads === 2
        ? jsonResponse({ message: 'Не удалось уточнить результат' }, 503)
        : jsonResponse(technicalDetails));
    });
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderDossier();
    fireEvent.click(await screen.findByRole('button', { name: 'Перезапустить проверку' }));

    expect(await screen.findByText(/Результат проверки неизвестен/)).toBeVisible();
    const retry = screen.getByRole('button', { name: 'Перезапустить проверку' });
    expect(retry).toBeDisabled();
    fireEvent.click(retry);
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Обновить досье' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Обновить досье' }));

    expect(await screen.findByText('Не удалось уточнить результат')).toBeVisible();
    expect(screen.getByText('Не удалось обновить досье')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Перезапустить проверку' })).toBeDisabled();
    expect(screen.getByText(/Результат проверки неизвестен/)).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Перезапустить проверку' }));
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);

    await waitFor(() => expect(screen.getByRole('button', { name: 'Обновить досье' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Обновить досье' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Перезапустить проверку' })).toBeEnabled());
    expect(screen.queryByText(/Результат проверки неизвестен/)).not.toBeInTheDocument();
    expect(reads).toBe(3);
    expect(onUpdated).not.toHaveBeenCalled();
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
  });

  it('keeps a successful retry result and reports a subsequent dossier read failure separately', async () => {
    const refresh = deferred<Response>();
    const result = { ...approvedApplication, approvedAmount: 345678, approvedRate: 22.75 };
    let reads = 0;
    const fetch = vi.fn<typeof globalThis.fetch>((_url, options) => {
      if (options?.method === 'POST') return Promise.resolve(jsonResponse(result));
      return reads++ === 0 ? Promise.resolve(jsonResponse(technicalDetails)) : refresh.promise;
    });
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderDossier();
    fireEvent.click(await screen.findByRole('button', { name: 'Перезапустить проверку' }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(result));
    expect(screen.getByText('Одобрено')).toBeVisible();
    expect(within(metric('Одобренная сумма')).getByText(/345\s*678\s*₽/)).toBeVisible();
    expect(within(metric('Ставка')).getByText('22.75%')).toBeVisible();
    expect(screen.getByText('Результат повторной проверки получен. Статус заявки обновлён.')).toBeVisible();
    expect(screen.getByText('Обновляем досье…')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Перезапустить проверку' })).not.toBeInTheDocument();

    await act(async () => { refresh.resolve(jsonResponse({ message: 'Досье временно недоступно' }, 503)); });

    expect(await screen.findByText('Досье временно недоступно')).toBeVisible();
    expect(screen.getByText('Не удалось обновить досье')).toBeVisible();
    expect(screen.getByText('Одобрено')).toBeVisible();
    expect(screen.getByText('Результат повторной проверки получен. Статус заявки обновлён.')).toBeVisible();
    expect(screen.queryByText('Не удалось перезапустить проверку')).not.toBeInTheDocument();
    expect(screen.queryByText('Технический сбой проверки')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Перезапустить проверку' })).not.toBeInTheDocument();
    expect(onUpdated).toHaveBeenCalledOnce();
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
  });

  it('does not reuse old retry eligibility when the successful POST still returns manual review and the dossier GET fails', async () => {
    const result = { ...approvedApplication, status: 'MANUAL_REVIEW', rejectionReason: 'Требуется ручное решение' };
    let reads = 0;
    const fetch = vi.fn<typeof globalThis.fetch>((_url, options) => Promise.resolve(options?.method === 'POST'
      ? jsonResponse(result)
      : reads++ === 0 ? jsonResponse(technicalDetails) : jsonResponse({ message: 'Досье не обновилось' }, 503)));
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderDossier();
    fireEvent.click(await screen.findByRole('button', { name: 'Перезапустить проверку' }));

    expect(await screen.findByText('Досье не обновилось')).toBeVisible();
    expect(screen.getByText('На ручной проверке')).toBeVisible();
    expect(screen.getByText('Требуется ручное решение')).toBeVisible();
    expect(screen.queryByText('Технический сбой проверки')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Перезапустить проверку' })).not.toBeInTheDocument();
    expect(onUpdated).toHaveBeenCalledWith(result);
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
  });

  it('offers a manual retry after a failed dossier read', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(jsonResponse({ message: 'Досье недоступно' }, 503))
      .mockResolvedValueOnce(jsonResponse(applicationDetails));
    vi.stubGlobal('fetch', fetch);
    renderDossier();
    expect(await screen.findByText('Досье недоступно')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByText('ACTIVE')).toBeVisible();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('aborts a previous selection read and ignores its late response', async () => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>().mockReturnValueOnce(pending.promise)
      .mockResolvedValueOnce(jsonResponse({ ...applicationDetails, id: 43, companyName: 'Новый заёмщик' }));
    vi.stubGlobal('fetch', fetch);
    const { rerender, onUpdated } = renderDossier();
    const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
    rerender(<ApplicationDossier applicationId={43} onUpdated={onUpdated} />);
    expect(await screen.findByText('Новый заёмщик')).toBeVisible();
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(jsonResponse(technicalDetails)); });
    expect(screen.getByRole('region', { name: 'Досье заявки №43' })).toBeVisible();
    expect(screen.queryByText('ФНС временно недоступна')).not.toBeInTheDocument();
  });

  it('aborts a retry when selecting another application', async () => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>((url, options) => options?.method === 'POST' ? pending.promise
      : Promise.resolve(jsonResponse(String(url).endsWith('/43') ? { ...applicationDetails, id: 43 } : technicalDetails)));
    vi.stubGlobal('fetch', fetch);
    const { rerender, onUpdated } = renderDossier();
    fireEvent.click(await screen.findByRole('button', { name: 'Перезапустить проверку' }));
    const signal = fetch.mock.calls.at(-1)![1]!.signal as AbortSignal;
    rerender(<ApplicationDossier applicationId={43} onUpdated={onUpdated} />);
    expect(await screen.findByText('Одобрено')).toBeVisible();
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(jsonResponse(approvedApplication)); });
    expect(onUpdated).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it.each(['GET', 'POST'] as const)('aborts an in-flight %s on unmount', async (method) => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>((_url, options) => options?.method === method
      ? pending.promise : Promise.resolve(jsonResponse(technicalDetails)));
    vi.stubGlobal('fetch', fetch);
    const { unmount, onUpdated } = renderDossier();
    if (method === 'POST') fireEvent.click(await screen.findByRole('button', { name: 'Перезапустить проверку' }));
    const signal = fetch.mock.calls.at(-1)![1]!.signal as AbortSignal;
    unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(jsonResponse(method === 'POST' ? approvedApplication : applicationDetails)); });
    expect(onUpdated).not.toHaveBeenCalled();
  });
});
