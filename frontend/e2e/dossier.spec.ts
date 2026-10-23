import AxeBuilder from '@axe-core/playwright';
import { employeeDossier, offeredApplication } from '../src/test/dossier';
import { apiError, deferred, expect, openAs, selectOption, sessions, test } from './fixtures';

const rationale = 'Учебное уменьшение лимита после рассмотрения источников.';

test('employee opens evidence by keyboard, validates a decision and saves one versioned counteroffer', async ({ page, api }) => {
  api.employeeData.APPROVED = [];
  api.employeeData.MANUAL_REVIEW = [employeeDossier.application];
  const release = deferred();
  api.decisionReply = async ({ body }) => {
    await release.promise;
    return { body: { ...employeeDossier, application: offeredApplication, allowedActions: [], audit: [...employeeDossier.audit, {
      id: 2, eventType: 'EMPLOYEE_COUNTEROFFER', actorRole: 'EMPLOYEE', fromStatus: 'MANUAL_REVIEW', toStatus: 'OFFER_PROPOSED',
      rationale: body.rationale, version: 2, createdAt: '2026-10-23T12:01:00Z',
    }] } };
  };
  await openAs(page, api, sessions.bank_employee, '/employee');
  const trigger = page.getByRole('button', { name: 'Открыть досье заявки №42', exact: true });
  await trigger.focus();
  await page.keyboard.press('Enter');
  const dossier = page.getByRole('region', { name: 'Досье заявки №42', exact: true });
  await expect(dossier.getByRole('heading', { name: 'Досье заявки №42', exact: true })).toBeFocused();
  await expect(dossier.getByText('580 / 850', { exact: true })).toBeVisible();
  await expect(dossier.getByRole('heading', { name: 'Синтетический отчёт БКИ', exact: true })).toBeVisible();
  await expect(dossier.getByText(/34,14\s*%/)).toBeVisible();
  await page.getByText('Сохранённый снимок входных данных', { exact: true }).click();
  await expect(dossier.getByText(/SHA-256:/)).toBeVisible();

  const submit = dossier.getByRole('button', { name: 'Сохранить учебное решение', exact: true });
  await selectOption(page, 'Учебное решение', 'COUNTEROFFER', 'Предложить меньшую сумму');
  await submit.click();
  await expect(page.getByLabel('Основание решения', { exact: true })).toBeFocused();
  await expect(page.getByLabel('Основание решения', { exact: true })).toHaveAttribute('aria-invalid', 'true');
  expect(api.calls.decisions).toEqual([]);
  await page.getByLabel('Основание решения', { exact: true }).fill(rationale);
  await page.getByLabel('Предлагаемая сумма, ₽', { exact: true }).fill('900000');
  try {
    await submit.dblclick();
    await expect.poll(() => api.calls.decisions.length).toBe(1);
    await expect(submit).toBeDisabled();
  } finally { release.resolve(); }
  expect(api.calls.decisions[0]).toEqual({ id: 42, body: {
    expectedVersion: 1, requestId: expect.stringMatching(/^[0-9a-f-]{36}$/), type: 'COUNTEROFFER', rationale, offerAmount: 900000,
  } });
  await expect(dossier.getByText('Учебное решение сохранено', { exact: true })).toBeVisible();
  await expect(dossier.getByText('Сотрудник · Предложены новые условия', { exact: true })).toBeVisible();
  await expect(submit).toBeDisabled();
  await page.getByRole('button', { name: 'Закрыть досье', exact: true }).click();
  await expect(trigger).toBeFocused();
});

test('employee conflict refresh preserves the draft and locks a completed decision', async ({ page, api }) => {
  api.employeeData.APPROVED = [];
  api.employeeData.MANUAL_REVIEW = [employeeDossier.application];
  api.dossierReply = () => ({ body: api.calls.dossier.length > 1
    ? { ...employeeDossier, application: offeredApplication, allowedActions: [] }
    : employeeDossier });
  api.decisionReply = () => apiError(409, 'Версия заявки изменилась.');
  await openAs(page, api, sessions.bank_employee, '/employee');
  await page.getByRole('button', { name: 'Открыть досье заявки №42', exact: true }).click();
  await page.getByLabel('Основание решения', { exact: true }).fill(rationale);
  await page.getByRole('button', { name: 'Сохранить учебное решение', exact: true }).click();
  await expect(page.getByText(/Ваш текст сохранён/)).toBeVisible();
  await expect(page.getByLabel('Основание решения', { exact: true })).toHaveValue(rationale);
  await expect(page.getByRole('button', { name: 'Сохранить учебное решение', exact: true })).toBeDisabled();
  await expect(page.getByRole('region', { name: 'Досье заявки №42', exact: true })).toContainText('Версия 2');
  expect(api.calls.decisions).toHaveLength(1);
  expect(api.calls.dossier).toEqual([42, 42]);
});

for (const choice of ['ACCEPT', 'REJECT'] as const) {
  test(`client ${choice} sends one consent command and keeps the terminal result visible`, async ({ page, api }) => {
    await page.clock.install();
    api.history = [offeredApplication];
    api.pollReply = () => ({ body: offeredApplication });
    const release = deferred();
    const terminal = { ...offeredApplication, status: choice === 'ACCEPT' ? 'APPROVED' as const : 'REJECTED' as const, version: 3,
      rejectionReason: choice === 'REJECT' ? 'Клиент отказался от учебного предложения.' : null };
    api.offerReply = async () => { await release.promise; return { body: terminal }; };
    await openAs(page, api, sessions.client_good, '/client');
    await expect(page.getByText(/82\s*724,12/)).toBeVisible();
    const button = page.getByRole('button', { name: choice === 'ACCEPT' ? 'Принять учебные условия' : 'Отказаться от предложения', exact: true });
    try {
      await button.dblclick();
      await expect.poll(() => api.calls.offers.length).toBe(1);
      await expect(button).toBeDisabled();
    } finally { release.resolve(); }
    expect(api.calls.offers).toEqual([{ id: 42, body: { expectedVersion: 2, requestId: expect.stringMatching(/^[0-9a-f-]{36}$/), decision: choice } }]);
    await expect(page.getByRole('alert')).toContainText(choice === 'ACCEPT' ? 'Одобрено' : 'Отклонено');
    await expect(page.getByRole('button', { name: 'Принять учебные условия', exact: true })).toHaveCount(0);
    await page.clock.fastForward(6000);
    expect(api.calls.polls).toEqual([]);
    await expect(page.getByRole('alert')).toHaveCount(1);
  });
}

test('client conflict loads revised terms and requires a fresh deliberate answer', async ({ page, api }) => {
  await page.clock.install();
  api.history = [offeredApplication];
  api.offerReply = () => apiError(409, 'Условия изменились.');
  api.pollReply = () => ({ body: { ...offeredApplication, version: 3, offer: { ...offeredApplication.offer!, amount: 800000 } } });
  await openAs(page, api, sessions.client_good, '/client');
  await page.getByRole('button', { name: 'Принять учебные условия', exact: true }).click();
  await expect(page.getByText(/Предложение изменилось/)).toBeVisible();
  await expect(page.getByRole('region', { name: 'Ваш ответ на учебное предложение №42', exact: true })).toContainText(/800\s*000/);
  expect(api.calls.offers).toHaveLength(1);
  expect(api.calls.polls).toEqual([42]);
});

test('dossier and consent remain readable at four widths with no serious or critical axe violations', async ({ page, api }, testInfo) => {
  test.setTimeout(90000);
  await page.clock.install();
  api.employeeData.APPROVED = [];
  api.employeeData.MANUAL_REVIEW = [employeeDossier.application];
  await openAs(page, api, sessions.bank_employee, '/employee');
  await page.getByRole('button', { name: 'Открыть досье заявки №42', exact: true }).click();
  await expect(page.getByText('580 / 850', { exact: true })).toBeVisible();
  await selectOption(page, 'Учебное решение', 'COUNTEROFFER', 'Предложить меньшую сумму');
  await page.getByLabel('Основание решения', { exact: true }).fill(rationale);

  async function inspect(name: string) {
    for (const width of [320, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      const viewport = await page.evaluate(() => innerWidth);
      // Core's matchMedia update is asynchronous. Advance mocked frames until
      // its responsive re-render settles, retaining the strict width limit.
      await expect.poll(async () => {
        await page.clock.runFor(50);
        return page.evaluate(() => document.documentElement.scrollWidth);
      }, { message: `${name} at ${width}px must not overflow`, timeout: 5000 }).toBeLessThanOrEqual(viewport + 1);
      const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      expect(audit.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact ?? '')),
        `${name} at ${width}px`).toEqual([]);
      if ([390, 1440].includes(width)) {
        // axe may focus the skip link during its keyboard audit. Return to the
        // ordinary page view before retaining visual evidence.
        await page.getByRole('heading', { level: 1 }).click();
        await page.screenshot({ path: testInfo.outputPath(`${name}-${width}.png`), fullPage: true });
      }
    }
  }
  await inspect('dossier');
  api.history = [offeredApplication];
  api.pollReply = () => ({ body: offeredApplication });
  await page.getByRole('button', { name: 'Выйти', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole('button', { name: 'Клиент: Надежный заемщик: Войти в кабинет', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Принять учебные условия', exact: true })).toBeVisible();
  await inspect('offer-consent');
});
