import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { useState } from 'react';
import type { Application } from '../../api/types';
import { AuthProvider } from '../../auth/AuthProvider';
import { approvedApplication, deferred, jsonResponse, storeSession } from '../../test/fixtures';
import { formatMoney } from './model';
import { ApplicationAgreement } from './ApplicationAgreement';

function renderAgreement(application: Application = approvedApplication) {
  storeSession();
  const onUpdated = vi.fn();
  const onRefresh = vi.fn();
  const onReconciliationChange = vi.fn();
  return { ...render(<MemoryRouter><AuthProvider><ApplicationAgreement application={application} onUpdated={onUpdated} onRefresh={onRefresh} onReconciliationChange={onReconciliationChange} /></AuthProvider></MemoryRouter>), onUpdated, onRefresh, onReconciliationChange };
}

function fillAgreement(code = '1234') {
  fireEvent.click(screen.getByRole('checkbox', { name: /Согласен с .*условиями договора/ }));
  fireEvent.change(screen.getByRole('textbox', { name: 'Код из СМС' }), { target: { value: code } });
}

describe('client agreement', () => {
  it('shows ordinary approved terms without suggesting a counteroffer', () => {
    const application = { ...approvedApplication, amount: approvedApplication.approvedAmount! };
    renderAgreement(application);
    expect(screen.queryByText('Банк предложил другие условия')).not.toBeInTheDocument();
    expect(screen.getByText('Запрошенная сумма').nextElementSibling).toHaveTextContent(formatMoney(application.amount).replace(/\s+/g, ' '));
    expect(screen.getByText('Одобренная сумма').nextElementSibling).toHaveTextContent(formatMoney(application.approvedAmount!).replace(/\s+/g, ' '));
    expect(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' })).toBeVisible();
  });

  it('explains a lower counteroffer and accepts it through signing without sending an amount', async () => {
    const signed = { ...approvedApplication, status: 'SIGNED' };
    const fetch = vi.fn().mockResolvedValue(jsonResponse(signed));
    vi.stubGlobal('fetch', fetch);
    const { onUpdated } = renderAgreement();
    expect(screen.getByRole('heading', { name: 'Банк предложил другие условия' })).toBeVisible();
    expect(screen.getByText('Запрошенная сумма').nextElementSibling).toHaveTextContent(formatMoney(approvedApplication.amount).replace(/\s+/g, ' '));
    expect(screen.getByText('Предложенная сумма').nextElementSibling).toHaveTextContent(formatMoney(approvedApplication.approvedAmount!).replace(/\s+/g, ' '));
    expect(screen.getByText(`${approvedApplication.approvedRate} % годовых`)).toBeVisible();
    expect(screen.getByRole('table', { name: 'График платежей' })).toBeVisible();
    expect(screen.getByRole('checkbox', { name: 'Согласен с условиями договора' })).toBeVisible();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(signed));
    expect(fetch.mock.calls[0]![1]).toMatchObject({ method: 'POST', body: JSON.stringify({ confirmTerms: true, smsCode: '1234' }) });
  });

  it('retains signed terms without presenting an offer awaiting acceptance', () => {
    renderAgreement({ ...approvedApplication, status: 'SIGNED' });
    expect(screen.queryByText('Банк предложил другие условия')).not.toBeInTheDocument();
    expect(screen.getByText('Запрошенная сумма')).toBeVisible();
    expect(screen.getByText('Одобренная сумма')).toBeVisible();
    expect(screen.getByRole('status')).toHaveTextContent('Договор подписан.');
    expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument();
  });

  it('shows actual manual-review conditions as preliminary and keeps signing unavailable', () => {
    renderAgreement({ ...approvedApplication, status: 'MANUAL_REVIEW', schedule: undefined });
    expect(screen.getByText('Предварительная сумма').nextElementSibling).toHaveTextContent(formatMoney(approvedApplication.approvedAmount!).replace(/\s+/g, ' '));
    expect(screen.getByText('Предварительная ставка').nextElementSibling).toHaveTextContent(`${approvedApplication.approvedRate} % годовых`);
    expect(screen.getByText('Условия предварительные; окончательное решение принимает сотрудник банка. Подписание пока недоступно.')).toBeVisible();
    expect(screen.getByText('График будет доступен после решения банка.')).toBeVisible();
    expect(screen.queryByText('Одобренная сумма')).not.toBeInTheDocument();
    expect(screen.queryByText('Предложенная сумма')).not.toBeInTheDocument();
    expect(screen.queryByText('Банк предложил другие условия')).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Подписание договора' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Подписать договор' })).not.toBeInTheDocument();
  });

  it('shows an approved-offer explanation as a neutral bank comment', () => {
    const comment = 'Банк предлагает меньшую сумму с учётом допустимой долговой нагрузки.';
    renderAgreement({ ...approvedApplication, rejectionReason: comment });
    expect(screen.getByRole('region', { name: 'Комментарий банка' })).toHaveTextContent(comment);
    expect(screen.getByRole('heading', { name: 'Банк предложил другие условия' })).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText('Отклонено')).not.toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Подписание договора' })).toBeVisible();
  });

  it.each([null, '', '   '])('omits an empty approved bank comment %j', (rejectionReason) => {
    renderAgreement({ ...approvedApplication, rejectionReason });
    expect(screen.queryByRole('region', { name: 'Комментарий банка' })).not.toBeInTheDocument();
  });

  it('requires consent and exactly four digits, then preserves leading zeroes in the request', async () => {
    const signed = { ...approvedApplication, status: 'SIGNED' };
    const fetch = vi.fn().mockResolvedValue(jsonResponse(signed));
    vi.stubGlobal('fetch', fetch);
    const { onUpdated, onReconciliationChange } = renderAgreement();

    expect(screen.getByText('Предложенная сумма').nextElementSibling).toHaveTextContent(formatMoney(approvedApplication.approvedAmount!).replace(/\s+/g, ' '));
    expect(screen.getByText(`${approvedApplication.approvedRate} % годовых`)).toBeVisible();
    const submit = screen.getByRole('button', { name: 'Подписать договор' });
    expect(submit).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(screen.getByText('Подтвердите согласие с условиями договора.')).toBeVisible();
    expect(screen.getByText('Введите код из СМС.')).toBeVisible();
    expect(screen.getByRole('checkbox')).toHaveFocus();
    expect(fetch).not.toHaveBeenCalled();

    fillAgreement('123');
    expect(submit).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(screen.getByText('Введите 4 цифры кода из СМС.')).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toHaveFocus();
    expect(fetch).not.toHaveBeenCalled();
    const code = screen.getByRole('textbox', { name: 'Код из СМС' });
    expect(code).toHaveAttribute('inputmode', 'numeric');
    expect(code).toHaveAttribute('maxlength', '4');
    fireEvent.change(code, { target: { value: '0001' } });
    expect(submit).toBeEnabled();
    fireEvent.click(submit);

    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(signed));
    expect(fetch).toHaveBeenCalledOnce();
    expect(fetch.mock.calls[0]![0]).toBe('/api/v1/client/applications/42/sign');
    expect(fetch.mock.calls[0]![1]).toMatchObject({ method: 'POST', body: JSON.stringify({ confirmTerms: true, smsCode: '0001' }) });
    expect(onReconciliationChange.mock.calls).toEqual([[true], [false]]);
  });

  it.each(['', '123', '12345', 'abcd', '１２３４', ' 1234', '1234 '])('rejects invalid code %j even when the form is submitted directly', (code) => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    renderAgreement();
    fillAgreement(code);
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(screen.getByRole('textbox', { name: 'Код из СМС' })).toHaveAttribute('aria-invalid', 'true');
    expect(fetch).not.toHaveBeenCalled();
  });

  it('keeps a valid code disabled until consent is given', () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    renderAgreement();
    fireEvent.change(screen.getByRole('textbox', { name: 'Код из СМС' }), { target: { value: '1234' } });
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(screen.getByText('Подтвердите согласие с условиями договора.')).toBeVisible();
    expect(fetch).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('checkbox'));
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled();
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
    const { onUpdated, onReconciliationChange } = renderAgreement();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Неверный код подтверждения');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled());
    expect(screen.queryByRole('button', { name: 'Обновить историю' })).not.toBeInTheDocument();
    expect(onUpdated).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenCalledOnce();
    expect(onReconciliationChange.mock.calls).toEqual([[true], [false]]);
  });

  it.each(['network', 'server', 'invalid response', 'unconfirmed status'] as const)('requires an authoritative read after an uncertain %s result', async (failure) => {
    const signed = { ...approvedApplication, status: 'SIGNED' };
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(jsonResponse(signed));
    if (failure === 'network') fetch.mockRejectedValueOnce(new TypeError('Network error'));
    else if (failure === 'server') fetch.mockResolvedValueOnce(jsonResponse({ message: 'Подписание временно недоступно' }, 503));
    else if (failure === 'invalid response') fetch.mockResolvedValueOnce(jsonResponse({ status: 'SIGNED' }));
    else fetch.mockResolvedValueOnce(jsonResponse(approvedApplication));
    vi.stubGlobal('fetch', fetch);
    const { onRefresh, onUpdated, onReconciliationChange } = renderAgreement();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Перед повторной отправкой обновите историю: договор мог быть подписан сервером.');
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    expect(onReconciliationChange).toHaveBeenLastCalledWith(true);
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(fetch).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', { name: 'Обновить историю' }));
    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(signed));
    expect(onReconciliationChange).toHaveBeenLastCalledWith(false);
    expect(onRefresh).toHaveBeenCalledOnce();
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(fetch.mock.calls[1]![0]).toBe('/api/v1/client/applications/42');
    expect(fetch.mock.calls[1]![1]!.method).toBe('GET');
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
  });

  it('honours a retained reconciliation requirement when the agreement is reopened', async () => {
    storeSession();
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(jsonResponse(approvedApplication));
    vi.stubGlobal('fetch', fetch);
    const onUpdated = vi.fn();
    const onRefresh = vi.fn();
    const onReconciliationChange = vi.fn();
    function ReopenedAgreement() {
      const [required, setRequired] = useState(true);
      return <ApplicationAgreement application={approvedApplication} onUpdated={onUpdated} onRefresh={onRefresh}
        requiresReconciliation={required} onReconciliationChange={(value) => {
          onReconciliationChange(value);
          setRequired(value);
        }} />;
    }
    render(<MemoryRouter><AuthProvider><ReopenedAgreement /></AuthProvider></MemoryRouter>);
    fillAgreement();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(fetch).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('результат предыдущей попытки подписания неизвестен');
    fireEvent.click(screen.getByRole('button', { name: 'Обновить историю' }));
    await waitFor(() => expect(onReconciliationChange).toHaveBeenCalledWith(false));
    expect(onUpdated).toHaveBeenCalledWith(approvedApplication);
    expect(onRefresh).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled();
    expect(fetch).toHaveBeenCalledOnce();
    expect(fetch.mock.calls[0]![1]!.method).toBe('GET');
  });

  it('keeps signing blocked after a failed reconciliation and permits a deliberate retry only after a confirmed APPROVED read', async () => {
    const confirmed = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>()
      .mockRejectedValueOnce(new TypeError('Network error'))
      .mockResolvedValueOnce(jsonResponse({ message: 'Проверка состояния недоступна' }, 503))
      .mockReturnValueOnce(confirmed.promise)
      .mockResolvedValueOnce(jsonResponse({ ...approvedApplication, status: 'SIGNED' }));
    vi.stubGlobal('fetch', fetch);
    const { onUpdated, onRefresh } = renderAgreement();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    await screen.findByRole('button', { name: 'Обновить историю' });
    fireEvent.click(screen.getByRole('button', { name: 'Обновить историю' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Не удалось проверить состояние договора.'));
    expect(onUpdated).not.toHaveBeenCalled();
    expect(onRefresh).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(fetch).toHaveBeenCalledTimes(2);

    await waitFor(() => expect(screen.getByRole('button', { name: 'Обновить историю' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Обновить историю' }));
    expect(screen.getByRole('button', { name: 'Обновить историю' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    await act(async () => { confirmed.resolve(jsonResponse(approvedApplication)); });
    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(approvedApplication));
    expect(onRefresh).toHaveBeenCalledOnce();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled();
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    await waitFor(() => expect(onUpdated).toHaveBeenCalledTimes(2));
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(2);
  });

  it('retains the requirement when a pending POST is closed and ignores its late response after reopening', async () => {
    storeSession();
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>()
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValue(jsonResponse(approvedApplication));
    vi.stubGlobal('fetch', fetch);
    const onUpdated = vi.fn();
    const onReconciliationChange = vi.fn();
    function AgreementHost() {
      const [open, setOpen] = useState(true);
      const [required, setRequired] = useState(false);
      return <>
        <button onClick={() => setOpen((value) => !value)}>{open ? 'Закрыть условия' : 'Открыть условия'}</button>
        {open && <ApplicationAgreement application={approvedApplication} onUpdated={onUpdated} onRefresh={() => undefined}
          requiresReconciliation={required} onReconciliationChange={(value) => {
            onReconciliationChange(value);
            setRequired(value);
          }} />}
      </>;
    }
    render(<MemoryRouter><AuthProvider><AgreementHost /></AuthProvider></MemoryRouter>);
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    expect(onReconciliationChange).toHaveBeenLastCalledWith(true);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Обновить историю' })).not.toBeInTheDocument();
    const signal = fetch.mock.calls[0]![1]!.signal as AbortSignal;
    fireEvent.click(screen.getByRole('button', { name: 'Закрыть условия' }));
    expect(signal.aborted).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Открыть условия' }));
    fillAgreement();
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeDisabled();
    fireEvent.submit(screen.getByRole('form', { name: 'Подписание договора' }));
    expect(fetch).toHaveBeenCalledOnce();
    await act(async () => { pending.resolve(jsonResponse({ ...approvedApplication, status: 'SIGNED' })); });
    expect(onUpdated).not.toHaveBeenCalled();
    expect(onReconciliationChange).toHaveBeenLastCalledWith(true);
    fireEvent.click(screen.getByRole('button', { name: 'Обновить историю' }));
    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(approvedApplication));
    expect(onReconciliationChange).toHaveBeenLastCalledWith(false);
    expect(screen.getByRole('button', { name: 'Подписать договор' })).toBeEnabled();
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1);
  });

  it.each(['unmount', 'api change'] as const)('aborts reconciliation on %s and ignores its late result', async (transition) => {
    const pending = deferred<Response>();
    const fetch = vi.fn<typeof globalThis.fetch>()
      .mockResolvedValueOnce(jsonResponse({ message: 'Подписание временно недоступно' }, 503))
      .mockReturnValueOnce(pending.promise);
    vi.stubGlobal('fetch', fetch);
    const { onUpdated, onRefresh, unmount } = renderAgreement();
    fillAgreement();
    fireEvent.click(screen.getByRole('button', { name: 'Подписать договор' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Обновить историю' }));
    const signal = fetch.mock.calls[1]![1]!.signal as AbortSignal;
    if (transition === 'unmount') unmount();
    else act(() => {
      localStorage.setItem('auth_token', 'another-session-token');
      window.dispatchEvent(new StorageEvent('storage', { key: 'auth_token' }));
    });
    expect(signal.aborted).toBe(true);
    await act(async () => { pending.resolve(jsonResponse({ ...approvedApplication, status: 'SIGNED' })); });
    expect(onUpdated).not.toHaveBeenCalled();
    expect(onRefresh).not.toHaveBeenCalled();
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
