import AxeBuilder from '@axe-core/playwright';
import type { Locator } from '@playwright/test';
import type { Application, ApplicationDetails } from '../src/api/types';
import {
  apiError,
  approvedAgreement,
  deferred,
  expect,
  expectSession,
  openAs,
  paymentSchedule,
  sessions,
  technicalDossier,
  test,
} from './fixtures';

function value(panel: Locator, label: string) {
  return panel.getByText(label, { exact: true }).locator('..').locator('dd');
}

test('restored approved history opens server terms and signs once only after consent and four SMS digits', async ({ page, api }) => {
  api.history = [approvedAgreement];
  const signed: Application = { ...approvedAgreement, status: 'SIGNED', updatedAt: '2026-10-22T14:35:00Z' };
  const releaseSign = deferred();
  api.signReply = async () => {
    await releaseSign.promise;
    api.history = [signed];
    return { body: signed };
  };
  await openAs(page, api, sessions.client_good, '/client');
  const openAgreement = page.getByRole('button', { name: 'Условия заявки №42', exact: true });
  await expect(openAgreement).toHaveText('№42');
  await openAgreement.focus();
  await page.keyboard.press('Enter');
  const panel = page.getByRole('region', { name: 'Условия заявки №42', exact: true });
  await expect(panel).toHaveCount(1);
  await expect(panel.getByRole('heading', { name: 'Условия заявки №42', level: 2 })).toBeVisible();
  // The approved amount differs from the requested amount in the history row.
  await expect(panel.getByRole('heading', { name: 'Банк предложил другие условия' })).toBeVisible();
  await expect(value(panel, 'Запрошенная сумма')).toHaveText(/^1\s500\s000\s₽$/);
  await expect(value(panel, 'Предложенная сумма')).toHaveText(/^100\s000\s₽$/);
  await expect(value(panel, 'Ставка')).toHaveText('18.5 % годовых');
  await expect(value(panel, 'Срок')).toHaveText('1 мес.');
  await expect(value(panel, 'Ежемесячный платёж')).toHaveText(/^101\s542\s₽$/);
  await expect(value(panel, 'Сумма процентов')).toHaveText(/^1\s542\s₽$/);
  await expect(value(panel, 'Всего к выплате')).toHaveText(/^101\s542\s₽$/);
  const scheduleRows = panel.getByRole('table', { name: 'График платежей', exact: true }).locator('tbody tr');
  await expect(scheduleRows).toHaveCount(paymentSchedule.items.length);
  const cells = scheduleRows.first().getByRole('cell');
  await expect(cells.nth(0)).toHaveText('1');
  await expect(cells.nth(1)).toHaveText('22.11.2026');
  await expect(cells.nth(2)).toHaveText(/^101\s542\s₽$/);
  await expect(cells.nth(3)).toHaveText(/^100\s000\s₽$/);
  await expect(cells.nth(4)).toHaveText(/^1\s542\s₽$/);
  await expect(cells.nth(5)).toHaveText(/^0\s₽$/);

  const consent = panel.getByRole('checkbox', { name: 'Согласен с условиями договора', exact: true });
  const sms = panel.getByRole('textbox', { name: 'Код из СМС', exact: true });
  const submit = panel.getByRole('button', { name: 'Подписать договор', exact: true });
  const smsCode = '0001';
  await expect(submit).toBeDisabled();
  expect(api.calls.sign).toEqual([]);
  await sms.fill(smsCode);
  await expect(submit).toBeDisabled();
  await consent.check();
  for (const invalid of ['', '   ', 'abc', '123']) {
    await sms.fill(invalid);
    await expect(submit).toBeDisabled();
    expect(api.calls.sign).toEqual([]);
  }

  await sms.fill(smsCode);
  await expect(submit).toBeEnabled();
  const signedResponse = page.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/client/applications/42/sign');
  try {
    await submit.dblclick();
    await expect.poll(() => api.calls.sign.length).toBe(1);
    await expect(submit).toBeDisabled();
    await expect(consent).toBeDisabled();
    await expect(sms).toBeDisabled();
    expect(api.calls.sign).toEqual([{ id: 42, input: { confirmTerms: true, smsCode } }]);
  } finally {
    releaseSign.resolve();
  }
  await (await signedResponse).finished();
  await expect(panel.getByRole('status')).toContainText('Договор подписан');
  await expect(panel.getByRole('form', { name: 'Подписание договора', exact: true })).toHaveCount(0);
  await expect(page.getByRole('table', { name: 'История заявок', exact: true })).toContainText('Договор подписан');
  await expectSession(page, sessions.client_good);
  expect(api.calls.sign).toHaveLength(1);
  expect(api.calls.create).toEqual([]);
  expect(api.calls.polls).toEqual([]);
  expect(api.calls.login).toEqual([]);
});

for (const failure of ['503', 'network'] as const) {
  test(`an ambiguous signing ${failure} blocks another POST until an authoritative GET succeeds`, async ({ page, api }) => {
    api.history = [approvedAgreement];
    api.signReply = () => apiError(503, 'Сервис подписания временно недоступен.');
    let refreshFails = true;
    const signed: Application = { ...approvedAgreement, status: 'SIGNED', updatedAt: '2026-10-22T14:35:00Z' };
    api.pollReply = () => refreshFails ? apiError(503, 'Не удалось проверить результат.') : { body: signed };
    await openAs(page, api, sessions.client_good, '/client');
    if (failure === 'network') {
      await page.route('**/api/v1/client/applications/42/sign', async (route) => {
        api.calls.sign.push({ id: 42, input: route.request().postDataJSON() });
        await route.abort('connectionfailed');
      });
    }
    await page.getByRole('button', { name: 'Условия заявки №42', exact: true }).click();
    const panel = page.getByRole('region', { name: 'Условия заявки №42', exact: true });
    await panel.getByRole('checkbox', { name: 'Согласен с условиями договора', exact: true }).check();
    await panel.getByRole('textbox', { name: 'Код из СМС', exact: true }).fill('6842');
    const submit = panel.getByRole('button', { name: 'Подписать договор', exact: true });
    await submit.click();
    await expect(panel.getByRole('alert')).toContainText('договор мог быть подписан сервером');
    await expect(submit).toBeDisabled();
    expect(api.calls.sign).toEqual([{ id: 42, input: { confirmTerms: true, smsCode: '6842' } }]);
    const refresh = panel.getByRole('button', { name: 'Обновить историю', exact: true });
    await refresh.click();
    await expect.poll(() => api.calls.polls.length).toBe(1);
    await expect(panel.getByRole('alert').filter({ hasText: 'Не удалось проверить результат.' })).toBeVisible();
    await expect(submit).toBeDisabled();
    expect(api.calls.sign).toHaveLength(1);

    refreshFails = false;
    api.history = [signed];
    await expect(refresh).toBeEnabled();
    await refresh.click();
    await expect(panel.getByRole('status')).toContainText('Договор подписан');
    await expect(panel.getByRole('button', { name: 'Подписать договор', exact: true })).toHaveCount(0);
    expect(api.calls.polls).toEqual([42, 42]);
    expect(api.calls.sign).toHaveLength(1);
    await expectSession(page, sessions.client_good);
  });
}

test('a rejected signing code stays editable and a corrected four-digit code can be submitted', async ({ page, api }) => {
  api.history = [approvedAgreement];
  api.signReply = () => apiError(400, 'Неверный код подтверждения.');
  await openAs(page, api, sessions.client_good, '/client');
  await page.getByRole('button', { name: 'Условия заявки №42', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Условия заявки №42', exact: true });
  const sms = panel.getByRole('textbox', { name: 'Код из СМС', exact: true });
  const submit = panel.getByRole('button', { name: 'Подписать договор', exact: true });
  await panel.getByRole('checkbox', { name: 'Согласен с условиями договора', exact: true }).check();
  await sms.fill('1234');
  await submit.click();
  await expect(panel.getByRole('alert')).toContainText('Неверный код подтверждения.');
  await expect(sms).toBeEnabled();
  await expect(submit).toBeEnabled();
  expect(api.calls.sign).toHaveLength(1);
  expect(api.calls.polls).toEqual([]);
  api.signReply = () => ({ body: { ...approvedAgreement, status: 'SIGNED', updatedAt: '2026-10-22T14:35:00Z' } });
  await sms.fill('0001');
  await submit.click();
  await expect(panel.getByRole('status')).toContainText('Договор подписан');
  expect(api.calls.sign).toEqual([
    { id: 42, input: { confirmTerms: true, smsCode: '1234' } },
    { id: 42, input: { confirmTerms: true, smsCode: '0001' } },
  ]);
});

test('a technical dossier shows server data and retry refreshes the registry and an already final decision', async ({ page, api }) => {
  let details = technicalDossier;
  api.detailsReply = () => ({ body: details });
  const finalApplication: Application = {
    ...approvedAgreement,
    id: technicalDossier.id,
    companyName: technicalDossier.companyName,
    inn: technicalDossier.inn,
    amount: technicalDossier.amount,
    createdAt: technicalDossier.createdAt,
    updatedAt: '2026-10-22T15:05:00Z',
  };
  const releaseRetry = deferred();
  api.retryReply = async () => {
    await releaseRetry.promise;
    api.employeeData.MANUAL_REVIEW = [];
    api.employeeData.APPROVED = [...api.employeeData.APPROVED, finalApplication];
    // A previous technical reason must not turn an approved dossier into a failure.
    details = { ...technicalDossier, ...finalApplication, canRetry: false };
    return { body: finalApplication };
  };
  await openAs(page, api, sessions.bank_employee, '/employee');
  await page.getByRole('button', { name: 'Досье заявки №44', exact: true }).click();
  const panel = page.getByRole('dialog', { name: 'Досье заявки №44', exact: true });
  await expect(panel.getByRole('heading', { name: 'Досье заявки №44', level: 2 })).toBeVisible();
  for (const name of ['Данные заявки', 'ФНС', 'БКИ', 'Скоринг']) {
    await expect(panel.getByRole('heading', { name, exact: true })).toBeVisible();
  }
  await expect(panel.getByText('Технический сбой проверки', { exact: true })).toBeVisible();
  await expect(value(panel, 'Статус в ФНС')).toHaveText('ACTIVE');
  await expect(value(panel, 'Блокировки счетов')).toHaveText('Нет');
  await expect(value(panel, 'Возраст бизнеса, мес.')).toHaveText('36');
  await expect(value(panel, 'Балл БКИ')).toHaveText('750');
  await expect(value(panel, 'Текущие платежи в месяц')).toHaveText(/^0\s₽$/);
  await expect(value(panel, 'Текущая просрочка, дней')).toHaveText('0');
  await expect(value(panel, 'Просрочки свыше 90 дней')).toHaveText('0');
  await expect(value(panel, 'Скоринговый балл')).toHaveText('85');
  await expect(value(panel, 'ПДН')).toHaveText('0%');
  await expect(value(panel, 'Максимальный лимит')).toHaveText(/^4\s500\s000\s₽$/);
  expect(api.calls.details).toEqual([44]);

  const registryBefore = api.calls.employee.length;
  const detailsBefore = api.calls.details.length;
  const retry = panel.getByRole('button', { name: 'Перезапустить проверку', exact: true });
  const retried = page.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/employee/applications/44/retry');
  try {
    await retry.dblclick();
    await expect.poll(() => api.calls.retry.length).toBe(1);
    await expect(retry).toBeDisabled();
  } finally {
    releaseRetry.resolve();
  }
  await (await retried).finished();
  await expect.poll(() => api.calls.employee.length).toBeGreaterThan(registryBefore);
  await expect.poll(() => api.calls.details.length).toBeGreaterThan(detailsBefore);
  await expect(panel).toContainText('Одобрено');
  await expect(panel.getByRole('table', { name: 'График платежей', exact: true })).toBeVisible();
  await expect(panel.getByText('Технический сбой проверки', { exact: true })).toHaveCount(0);
  await expect(retry).toHaveCount(0);
  expect(api.calls.retry).toEqual([44]);
  expect(api.calls.employee.slice(registryBefore)).toEqual([{ status: null, page: 0, size: 10 }]);
  expect(api.calls.details.slice(detailsBefore)).toEqual([44]);
  await panel.getByRole('button', { name: 'Закрыть досье', exact: true }).click();
  const row = page.getByRole('table', { name: 'Реестр кредитных заявок', exact: true })
    .getByRole('row').filter({ has: page.getByRole('button', { name: 'Досье заявки №44', exact: true }) });
  await expect(row).toContainText('Одобрено');
  await expectSession(page, sessions.bank_employee);
});

for (const reason of ['BORDERLINE_SCORE', 'TECH_FAILURE'] as const) {
  test(`an ineligible ${reason} dossier exposes no retry action and tolerates Java null snapshots`, async ({ page, api }) => {
    api.detailsReply = () => ({ body: {
      ...technicalDossier,
      reviewReasonCode: reason,
      // A true permission alone must not make borderline scoring retryable.
      canRetry: reason === 'BORDERLINE_SCORE',
      user: null,
      fns: null,
      bki: { bkiScore: null, monthlyPaymentRub: 0, currentOverdueDays: null },
      scoring: null,
      schedule: null,
    } });
    await openAs(page, api, sessions.bank_employee, '/employee');
    await page.getByRole('button', { name: 'Досье заявки №44', exact: true }).click();
    const panel = page.getByRole('dialog', { name: 'Досье заявки №44', exact: true });
    await expect(panel.getByRole('heading', { name: 'БКИ', exact: true })).toBeVisible();
    await expect(value(panel, 'Балл БКИ')).toHaveText('—');
    await expect(value(panel, 'Текущие платежи в месяц')).toHaveText(/^0\s₽$/);
    await expect(value(panel, 'Текущая просрочка, дней')).toHaveText('—');
    await expect(panel.getByText('Нет данных', { exact: true })).toHaveCount(2);
    await expect(panel.getByText('Технический сбой проверки', { exact: true })).toHaveCount(reason === 'TECH_FAILURE' ? 1 : 0);
    await expect(panel.getByRole('button', { name: 'Перезапустить проверку', exact: true })).toHaveCount(0);
    const detailsBefore = api.calls.details.length;
    await panel.getByRole('button', { name: 'Обновить досье', exact: true }).click();
    await expect.poll(() => api.calls.details.length).toBeGreaterThan(detailsBefore);
    await expect(value(panel, 'Балл БКИ')).toHaveText('—');
    expect(api.calls.retry).toEqual([]);
  });
}

test('dossier and retry errors recover with explicit GET requests without automatically repeating the POST', async ({ page, api }) => {
  let detailsFail = true;
  api.detailsReply = () => detailsFail ? apiError(503, 'Досье временно недоступно.') : { body: technicalDossier };
  api.retryReply = () => apiError(503, 'Повторная проверка временно недоступна.');
  await openAs(page, api, sessions.bank_employee, '/employee');
  await page.getByRole('button', { name: 'Досье заявки №44', exact: true }).click();
  const panel = page.getByRole('dialog', { name: 'Досье заявки №44', exact: true });
  await expect(panel.getByRole('alert')).toContainText('Досье временно недоступно.');
  expect(api.calls.details).toEqual([44]);
  expect(api.calls.retry).toEqual([]);
  detailsFail = false;
  await panel.getByRole('button', { name: 'Повторить', exact: true }).click();
  await expect(panel.getByText('Технический сбой проверки', { exact: true })).toBeVisible();
  expect(api.calls.details).toEqual([44, 44]);
  await panel.getByRole('button', { name: 'Перезапустить проверку', exact: true }).click();
  await expect(panel.getByRole('alert').filter({ hasText: 'Повторная проверка временно недоступна.' }))
    .toContainText('Повторная проверка временно недоступна.');
  expect(api.calls.retry).toEqual([44]);
  await expect(panel.getByRole('button', { name: 'Перезапустить проверку', exact: true })).toBeDisabled();
  detailsFail = true;
  await panel.getByRole('button', { name: 'Обновить досье', exact: true }).click();
  await expect.poll(() => api.calls.details.length).toBe(3);
  await expect(panel.getByRole('alert').filter({ hasText: 'Досье временно недоступно.' })).toBeVisible();
  await expect(panel.getByRole('button', { name: 'Перезапустить проверку', exact: true })).toBeDisabled();
  expect(api.calls.retry).toEqual([44]);
  detailsFail = false;
  await panel.getByRole('button', { name: 'Обновить досье', exact: true }).click();
  await expect.poll(() => api.calls.details.length).toBe(4);
  await expect(panel.getByRole('button', { name: 'Перезапустить проверку', exact: true })).toBeEnabled();
  expect(api.calls.retry).toEqual([44]);
  await expectSession(page, sessions.bank_employee);
});

test('selected agreement and dossier remain responsive and have no serious or critical axe violations', async ({ page, api }) => {
  test.setTimeout(60_000);
  api.history = [approvedAgreement];
  const finalDetails: ApplicationDetails = {
    ...technicalDossier,
    status: 'APPROVED',
    canRetry: false,
    termMonths: 1,
    approvedAmount: 100_000,
    approvedRate: 18.5,
    schedule: paymentSchedule,
  };
  api.detailsReply = () => ({ body: finalDetails });
  for (const workspace of ['client', 'employee'] as const) {
    await openAs(page, api, workspace === 'client' ? sessions.client_good : sessions.bank_employee, `/${workspace}`);
    const name = workspace === 'client' ? 'Условия заявки №42' : 'Досье заявки №44';
    await page.getByRole('button', { name, exact: true }).click();
    const panel = page.getByRole(workspace === 'employee' ? 'dialog' : 'region', { name, exact: true });
    await expect(panel.getByRole('table', { name: 'График платежей', exact: true })).toBeVisible();
    for (const width of [320, 390, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
      const dimensions = await page.evaluate(() => ({ viewport: window.innerWidth, content: document.documentElement.scrollWidth }));
      expect(dimensions.content, `${workspace} selected panel at ${width}px must not overflow`).toBeLessThanOrEqual(dimensions.viewport + 1);
      const panelWidth = await panel.evaluate((element) => element.getBoundingClientRect().width);
      expect(panelWidth, `${workspace} panel at ${width}px must fit the viewport`).toBeLessThanOrEqual(width + 1);
      const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      const serious = result.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical');
      expect(serious, `${workspace} selected panel at ${width}px: ${serious.map((violation) => `${violation.id}: ${violation.help}`).join('; ')}`).toEqual([]);
    }
    if (workspace === 'employee') await panel.getByRole('button', { name: 'Закрыть досье', exact: true }).click();
    await page.getByRole('button', { name: 'Выйти', exact: true }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expectSession(page, null);
  }
});

test('dossier drawer keeps keyboard focus inside, closes with Escape and restores its opener', async ({ page, api }) => {
  await openAs(page, api, sessions.bank_employee, '/employee');
  const opener = page.getByRole('button', { name: 'Досье заявки №44', exact: true });
  await opener.focus();
  await page.keyboard.press('Enter');
  const drawer = page.getByRole('dialog', { name: 'Досье заявки №44', exact: true });
  await expect(drawer).toBeVisible();
  await expect(drawer.getByRole('heading', { name: 'БКИ', exact: true })).toBeVisible();
  const focusInside = () => drawer.evaluate((dialog) => dialog.contains(document.activeElement));
  expect(await focusInside()).toBe(true);
  const close = drawer.getByRole('button', { name: 'Закрыть досье', exact: true });
  await close.focus();
  await page.keyboard.press('Shift+Tab');
  expect(await focusInside()).toBe(true);
  await page.keyboard.press('Tab');
  expect(await focusInside()).toBe(true);
  const readsBefore = api.calls.details.length;
  await page.keyboard.press('Escape');
  await expect(drawer).toHaveCount(0);
  await expect(opener).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(drawer).toBeVisible();
  await expect.poll(() => api.calls.details.length).toBe(readsBefore + 1);
  await drawer.getByRole('button', { name: 'Закрыть досье', exact: true }).click();
  await expect(drawer).toHaveCount(0);
  await expect(opener).toBeFocused();
});
