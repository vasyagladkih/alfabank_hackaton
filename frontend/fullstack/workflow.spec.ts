import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import type { Page, TestInfo } from '@playwright/test';
import type { Application, EmployeeDossier } from '../src/api/types';

const loginNames = {
  client_good: 'Клиент: Надежный заемщик: Войти в кабинет',
  client_bad: 'Клиент: Высокий риск: Войти в кабинет',
  bank_employee: 'Сотрудник банка / Андеррайтер: Войти в кабинет',
};

async function login(page: Page, code: keyof typeof loginNames) {
  await page.goto('/login');
  const response = page.waitForResponse((value) => value.url().endsWith('/api/v1/auth/demo-login'));
  const button = page.getByRole('button', { name: loginNames[code], exact: true });
  await button.focus();
  await page.keyboard.press('Enter');
  expect((await response).status()).toBe(200);
  await expect(page).toHaveURL(code === 'bank_employee' ? /\/employee$/ : /\/client$/);
}

async function create(page: Page, amount: number): Promise<Application> {
  await page.getByLabel('Сумма кредита, ₽', { exact: true }).fill(String(amount));
  await page.getByLabel('Срок, месяцев', { exact: true }).fill('12');
  await page.getByRole('combobox', { name: 'Цель кредита', exact: true }).selectOption('EQUIPMENT');
  const created = page.waitForResponse((response) => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/v1/client/applications');
  await page.getByRole('button', { name: 'Отправить заявку', exact: true }).click();
  const response = await created;
  expect(response.status()).toBe(201);
  return await response.json() as Application;
}

function historyRow(page: Page, id: number) {
  return page.getByRole('table', { name: 'История заявок', exact: true }).getByRole('row')
    .filter({ has: page.getByText(`№${id}`, { exact: true }) });
}

async function checkLayout(page: Page, name: string, info: TestInfo) {
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    const dimensions = await page.evaluate(() => ({ width: window.innerWidth, content: document.documentElement.scrollWidth }));
    expect(dimensions.content, `${name} at ${width}px`).toBeLessThanOrEqual(dimensions.width + 1);
    const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(result.violations.filter((issue) => issue.impact === 'serious' || issue.impact === 'critical'), `${name} at ${width}px`).toEqual([]);
    if (width === 390 || width === 1440) {
      if (name === 'employee-dossier') {
        await page.locator('.dossier-panel').screenshot({ path: info.outputPath(`${name}-${width}.png`) });
      } else {
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.screenshot({ path: info.outputPath(`${name}-${width}.png`), fullPage: name === 'login' || name === 'forbidden' });
      }
    }
  }
}

test('real login, client creation, persistent result, keyboard, all four screen layouts', async ({ page }, info) => {
  await page.goto('/login');
  await checkLayout(page, 'login', info);
  await login(page, 'client_good');
  const app = await create(page, 1_500_000);
  expect(app.status).toBe('PROCESSING');
  await expect(historyRow(page, app.id)).toContainText('Одобрено');
  await expect(page.getByText(`Заявка №${app.id} одобрена. Решение также сохранено в истории.`, { exact: true })).toBeVisible();
  await page.reload();
  await expect(historyRow(page, app.id)).toContainText('Одобрено');
  await page.evaluate(() => window.scrollTo(0, 0));
  await checkLayout(page, 'client', info);
  await page.goto('/employee');
  await expect(page).toHaveURL(/\/403$/);
  await checkLayout(page, 'forbidden', info);
  await page.getByRole('button', { name: 'Выйти', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await login(page, 'bank_employee');
  await expect(page.getByRole('table', { name: 'Реестр кредитных заявок', exact: true })).toBeVisible();
  await checkLayout(page, 'employee-queue', info);
});

test('employee dossier -> real counteroffer -> client accepts -> both see persisted audit', async ({ browser, request }, info) => {
  const authentication = await request.post('/api/v1/auth/demo-login', { data: { userCode: 'bank_employee' } });
  expect(authentication.status()).toBe(200);
  const auth = await authentication.json() as { token: string };
  const headers = { Authorization: `Bearer ${auth.token}` };
  const queue = await request.get('/api/v1/employee/applications?status=MANUAL_REVIEW', { headers });
  const applications = await queue.json() as Application[];
  let selected: EmployeeDossier | undefined;
  for (const application of applications) {
    const response = await request.get(`/api/v1/employee/applications/${application.id}`, { headers });
    const dossier = await response.json() as EmployeeDossier;
    if (dossier.allowedActions.includes('COUNTEROFFER')) { selected = dossier; break; }
  }
  expect(selected, 'fresh seeded database must have an unconsumed manual scenario').toBeDefined();
  if (!selected) throw new Error('No eligible demo scenario');
  const id = selected.application.id;
  const amount = Math.min(selected.application.amount - 100_000, selected.assessment?.affordableAmount ?? 0);
  expect(amount).toBeGreaterThanOrEqual(100_000);

  const employeeContext = await browser.newContext({ baseURL: info.project.use.baseURL });
  const clientContext = await browser.newContext({ baseURL: info.project.use.baseURL });
  const employee = await employeeContext.newPage();
  const client = await clientContext.newPage();
  try {
    await login(client, 'client_good');
    await expect(historyRow(client, id)).toContainText('На рассмотрении');
    await login(employee, 'bank_employee');
    await employee.getByLabel('Поиск по заявкам', { exact: true }).fill(String(id));
    await employee.getByRole('button', { name: 'Найти', exact: true }).click();
    const opener = employee.getByRole('button', { name: `Открыть досье заявки №${id}`, exact: true });
    await opener.focus();
    await employee.keyboard.press('Enter');
    const heading = employee.getByRole('heading', { name: `Досье заявки №${id}`, exact: true });
    await expect(heading).toBeFocused();
    await expect(employee.getByRole('heading', { name: 'Источники и свежесть', exact: true })).toBeVisible();
    await expect(employee.getByRole('table', { name: 'Разбор балльной оценки', exact: true })).toBeVisible();
    await checkLayout(employee, 'employee-dossier', info);
    await employee.getByRole('combobox', { name: 'Учебное решение', exact: true }).selectOption('COUNTEROFFER');
    await employee.getByLabel('Предлагаемая сумма, ₽', { exact: true }).fill(String(amount));
    await employee.getByLabel('Основание решения', { exact: true }).fill('Учебное предложение по синтетическому снимку. Реального финансирования нет.');
    const decision = employee.waitForResponse((response) => response.url().endsWith(`/employee/applications/${id}/decisions`));
    await employee.getByRole('button', { name: 'Сохранить учебное решение', exact: true }).click();
    expect((await decision).status()).toBe(200);
    await expect(employee.getByText('Учебное решение сохранено', { exact: true })).toBeVisible();
    const offer = client.getByRole('region', { name: `Ваш ответ на учебное предложение №${id}`, exact: true });
    await expect(offer).toBeVisible();
    await offer.screenshot({ path: info.outputPath('client-offer.png') });
    const consent = client.waitForResponse((response) => response.url().endsWith(`/client/applications/${id}/offer-response`));
    await offer.getByRole('button', { name: 'Принять учебные условия', exact: true }).click();
    expect((await consent).status()).toBe(200);
    await expect(historyRow(client, id)).toContainText('Одобрено');
    await client.reload();
    await expect(historyRow(client, id)).toContainText('Одобрено');
    await employee.getByRole('button', { name: 'Обновить досье', exact: true }).click();
    await expect(employee.locator('.dossier-current-status')).toContainText('Одобрено');
    await expect(employee.getByText('Клиент · Одобрено', { exact: true })).toBeVisible();
    await employee.locator('.dossier-panel').screenshot({ path: info.outputPath('dossier-after-client-accept.png') });
    await employee.getByRole('button', { name: 'Закрыть досье', exact: true }).click();
    await expect(opener).toBeFocused();
  } finally {
    await employeeContext.close(); await clientContext.close();
  }
});

test('client declines a real automatic offer and the other demo login gets an explained rejection', async ({ page }, info) => {
  await login(page, 'client_good');
  const app = await create(page, 10_000_000);
  const offer = page.getByRole('region', { name: `Ваш ответ на учебное предложение №${app.id}`, exact: true });
  await expect(offer).toBeVisible();
  await offer.getByRole('button', { name: 'Отказаться от предложения', exact: true }).click();
  await expect(historyRow(page, app.id)).toContainText('Отклонено');
  await page.getByRole('button', { name: 'Выйти', exact: true }).click();
  await login(page, 'client_bad');
  const bad = await create(page, 1_000_000);
  await expect(historyRow(page, bad.id)).toContainText('Отклонено');
  await historyRow(page, bad.id).screenshot({ path: info.outputPath('client-rejection.png') });
  await page.reload();
  await expect(historyRow(page, bad.id)).toContainText('Отклонено');
});
