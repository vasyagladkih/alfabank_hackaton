import AxeBuilder from '@axe-core/playwright';
import type { Page } from '@playwright/test';
import type { ApplicationStatus, UserCode } from '../src/api/types';
import {
  apiError,
  applications,
  deferred,
  expect,
  expectSession,
  openAs,
  processingApplication,
  selectOption,
  sessions,
  test,
} from './fixtures';

const cards: Record<UserCode, string> = {
  client_good: 'Клиент: Надежный заемщик: Войти в кабинет',
  client_bad: 'Клиент: Высокий риск: Войти в кабинет',
  bank_employee: 'Сотрудник банка / Андеррайтер: Войти в кабинет',
};
const headings = { CLIENT: 'Кредит для вашего бизнеса', EMPLOYEE: 'Заявки на финансирование' };
const statusLabels: Record<ApplicationStatus, string> = {
  PROCESSING: 'В обработке', MANUAL_REVIEW: 'На ручной проверке', APPROVED: 'Одобрено',
  SIGNED: 'Договор подписан', REJECTED: 'Отклонено',
};

function action(page: Page, name: string) {
  return page.getByRole('button', { name, exact: true }).or(page.getByRole('link', { name, exact: true }));
}

for (const code of Object.keys(cards) as UserCode[]) {
  test(`demo login ${code}: server profile, route, and Bearer requests`, async ({ page, api }) => {
    await page.goto('/login');
    for (const name of Object.values(cards)) await expect(page.getByRole('button', { name, exact: true })).toBeVisible();
    const login = page.getByRole('button', { name: cards[code], exact: true });
    if (code === 'client_good') { await login.focus(); await page.keyboard.press('Enter'); }
    else await login.click();

    const session = sessions[code];
    await expect(page).toHaveURL(session.user.role === 'CLIENT' ? /\/client$/ : /\/employee$/);
    await expect(page.getByRole('heading', { name: headings[session.user.role], level: 1 })).toBeVisible();
    await expectSession(page, session);
    expect(api.calls.login).toEqual([code]);
    if (session.user.role === 'CLIENT') {
      await expect(page.getByText(session.user.companyName!, { exact: true }).first()).toBeVisible();
      await expect(page.getByText(new RegExp(session.user.inn!)).first()).toBeVisible();
      await expect.poll(() => api.calls.history).toBeGreaterThan(0);
    } else {
      await expect(page.getByText(session.user.username, { exact: true }).first()).toBeVisible();
      await expect.poll(() => api.calls.employee.length).toBeGreaterThan(0);
    }
  });
}

test('guests cannot open either workspace and the root opens login', async ({ page, api }) => {
  for (const path of ['/client', '/employee', '/']) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('button', { name: cards.client_good, exact: true })).toBeVisible();
  }
  await expectSession(page, null);
  expect(api.calls.history).toBe(0);
  expect(api.calls.employee).toEqual([]);
});

test('refresh restores the server profile and logout clears both storage keys', async ({ page, api }) => {
  api.history = [applications.APPROVED];
  await openAs(page, api, sessions.client_good, '/client');
  await expect(page.getByRole('heading', { name: headings.CLIENT, level: 1 })).toBeVisible();
  await expect(page.getByRole('table').getByText('Одобрено', { exact: true })).toBeVisible();
  const requestsBeforeRefresh = api.calls.history;

  await page.reload();
  await expect(page.getByRole('heading', { name: headings.CLIENT, level: 1 })).toBeVisible();
  await expect.poll(() => api.calls.history).toBeGreaterThan(requestsBeforeRefresh);
  await expectSession(page, sessions.client_good);
  expect(api.calls.login).toEqual([]);

  await page.getByRole('button', { name: 'Выйти', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expectSession(page, null);
});

for (const role of ['CLIENT', 'EMPLOYEE'] as const) {
  test(`${role} cross-role 403 preserves session, returns home, and switches user`, async ({ page, api }) => {
    const session = role === 'CLIENT' ? sessions.client_good : sessions.bank_employee;
    const wrongPath = role === 'CLIENT' ? '/employee' : '/client';
    const ownPath = role === 'CLIENT' ? '/client' : '/employee';
    await openAs(page, api, session, wrongPath);

    await expect(page).toHaveURL(/\/403$/);
    await expect(page.getByRole('heading', { name: '403 — Доступ ограничен', level: 1 })).toBeVisible();
    await expectSession(page, session);
    expect(role === 'CLIENT' ? api.calls.employee.length : api.calls.history).toBe(0);

    await action(page, 'Вернуться в свой кабинет').click();
    await expect(page).toHaveURL(new RegExp(`${ownPath}$`));
    await expect(page.getByRole('heading', { name: headings[role], level: 1 })).toBeVisible();
    await expectSession(page, session);

    await page.goto(wrongPath);
    await expect(page).toHaveURL(/\/403$/);
    await action(page, 'Сменить пользователя').click();
    await expect(page).toHaveURL(/\/login$/);
    await expectSession(page, null);
  });
}

for (const status of [401, 403] as const) {
  test(`HTTP ${status} from the API ${status === 401 ? 'expires' : 'retains'} the session`, async ({ page, api }) => {
    api.historyReply = () => apiError(status, status === 401 ? 'Сессия истекла.' : 'Доступ запрещен.');
    await openAs(page, api, sessions.client_good, '/client');

    await expect(page).toHaveURL(status === 401 ? /\/login$/ : /\/403$/);
    await expectSession(page, status === 401 ? null : sessions.client_good);
    expect(api.calls.history).toBe(1);
    if (status === 403) {
      await expect(page.getByRole('heading', { name: '403 — Доступ ограничен', level: 1 })).toBeVisible();
      await expect(action(page, 'Вернуться в свой кабинет')).toBeVisible();
    } else {
      await expect(page.getByRole('button', { name: cards.client_good, exact: true })).toBeVisible();
    }
  });
}

for (const finalStatus of ['APPROVED', 'REJECTED'] as const) {
  test(`create once, poll, and show ${finalStatus}${finalStatus === 'REJECTED' ? ' with its reason' : ''}`, async ({ page, api }) => {
    await page.clock.install();
    const releasePost = deferred();
    api.createReply = async () => {
      await releasePost.promise;
      api.history = [processingApplication];
      return { status: 201, body: processingApplication };
    };
    const terminal = {
      ...processingApplication,
      status: finalStatus,
      updatedAt: '2026-10-22T14:30:02Z',
      rejectionReason: finalStatus === 'REJECTED' ? applications.REJECTED.rejectionReason : null,
    };
    api.pollReply = (id) => {
      expect(id).toBe(42);
      api.history = [terminal];
      return { body: terminal };
    };
    await openAs(page, api, sessions.client_good, '/client');
    await page.getByLabel('Сумма кредита, ₽', { exact: true }).fill('1500000');
    await page.getByLabel('Срок, месяцев', { exact: true }).fill('12');
    await selectOption(page, 'Цель кредита', 'EQUIPMENT', /оборудован/i);
    const submit = page.getByRole('button', { name: 'Отправить заявку', exact: true });
    const created = page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/client/applications');
    try {
      await submit.dblclick();
      await expect.poll(() => api.calls.create.length).toBe(1);
      await expect(submit).toBeDisabled();
      expect(api.calls.polls).toEqual([]);
    } finally {
      releasePost.resolve();
    }
    await (await created).finished();
    expect(api.calls.create).toEqual([{ amount: 1_500_000, termMonths: 12, purpose: 'EQUIPMENT' }]);
    await expect(page.getByText('Заявка №42 в обработке', { exact: true })).toBeVisible();

    const polled = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/client/applications/42');
    await page.clock.fastForward(2_000);
    await (await polled).finished();
    await expect(page.getByText(statusLabels[finalStatus], { exact: true }).first()).toBeVisible();
    if (finalStatus === 'REJECTED') {
      await expect(page.getByText(terminal.rejectionReason!, { exact: true }).first()).toBeVisible();
    }
    await page.clock.fastForward(6_000);
    expect(api.calls.polls).toEqual([42]);
    expect(api.calls.create).toHaveLength(1);
  });
}

test('refresh resumes a processing application and logout stops future polling', async ({ page, api }) => {
  await page.clock.install();
  api.history = [processingApplication];
  await openAs(page, api, sessions.client_good, '/client');
  await expect(page.getByText('Заявка №42 в обработке', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('Заявка №42 в обработке', { exact: true })).toBeVisible();
  await expectSession(page, sessions.client_good);

  const polled = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/v1/client/applications/42');
  await page.clock.fastForward(2_000);
  await (await polled).finished();
  await page.getByRole('button', { name: 'Выйти', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expectSession(page, null);
  await page.clock.fastForward(6_000);
  expect(api.calls.polls).toEqual([42]);
});

test('employee ALL uses one paginated request, preserves server order, filters, and refreshes', async ({ page, api }) => {
  await openAs(page, api, sessions.bank_employee, '/employee');
  await expect(page.getByRole('heading', { name: headings.EMPLOYEE, level: 1 })).toBeVisible();
  const rows = page.getByRole('table').locator('tbody tr');
  await expect(rows).toHaveCount(5);
  expect(api.calls.employee).toEqual([{ status: null, page: 0, size: 10 }]);
  // Deliberately differs from a client-side newest-first sort.
  await expect(rows.nth(0).getByRole('cell').first()).toContainText('41');
  await expect(rows.nth(1).getByRole('cell').first()).toContainText('42');
  await expect(rows.nth(2).getByRole('cell').first()).toContainText('43');
  await expect(rows.nth(3).getByRole('cell').first()).toContainText('44');
  await expect(rows.nth(4).getByRole('cell').first()).toContainText('45');
  await expect(rows.nth(1)).toContainText('Одобрено');
  await expect(page.getByText('Найдено заявок: 5', { exact: true })).toBeVisible();
  await expect(page.getByText('Страница 1 из 1', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Назад', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Далее', exact: true })).toBeDisabled();

  for (const status of ['PROCESSING', 'MANUAL_REVIEW', 'APPROVED', 'SIGNED', 'REJECTED'] as const) {
    const before = api.calls.employee.length;
    await selectOption(page, 'Статус заявки', status, statusLabels[status]);
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(applications[status].companyName!);
    await expect(rows.first()).toContainText(statusLabels[status]);
    await expect(page.getByText('Найдено заявок: 1', { exact: true })).toBeVisible();
    expect(api.calls.employee.slice(before)).toEqual([{ status, page: 0, size: 10 }]);
  }

  const beforeRefresh = api.calls.employee.length;
  api.employeeData.REJECTED = [{ ...applications.REJECTED, id: 46, companyName: 'ООО «Новая заявка»' }];
  await page.getByRole('button', { name: 'Обновить', exact: true }).click();
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('ООО «Новая заявка»');
  expect(api.calls.employee.slice(beforeRefresh)).toEqual([{ status: 'REJECTED', page: 0, size: 10 }]);
});

test('employee pagination uses server totals, refreshes the current page, and resets on a filter change', async ({ page, api }) => {
  api.employeeData = {
    PROCESSING: Array.from({ length: 11 }, (_, index) => ({
      ...applications.PROCESSING,
      id: 100 + index,
      companyName: `ООО «Компания ${index + 1}»`,
    })),
    MANUAL_REVIEW: [applications.MANUAL_REVIEW],
    APPROVED: [],
    SIGNED: [],
    REJECTED: [],
  };
  await openAs(page, api, sessions.bank_employee, '/employee');
  const rows = page.getByRole('table').locator('tbody tr');
  const previous = page.getByRole('button', { name: 'Назад', exact: true });
  const next = page.getByRole('button', { name: 'Далее', exact: true });
  await expect(rows).toHaveCount(10);
  await expect(rows.first()).toContainText('ООО «Компания 1»');
  await expect(rows.last()).toContainText('ООО «Компания 10»');
  await expect(page.getByText('Найдено заявок: 12', { exact: true })).toBeVisible();
  await expect(page.getByText('Страница 1 из 2', { exact: true })).toBeVisible();
  await expect(previous).toBeDisabled();
  await expect(next).toBeEnabled();
  expect(api.calls.employee).toEqual([{ status: null, page: 0, size: 10 }]);

  await next.click();
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText('ООО «Компания 11»');
  await expect(rows.last()).toContainText(applications.MANUAL_REVIEW.companyName!);
  await expect(page.getByText('Найдено заявок: 12', { exact: true })).toBeVisible();
  await expect(page.getByText('Страница 2 из 2', { exact: true })).toBeVisible();
  await expect(previous).toBeEnabled();
  await expect(next).toBeDisabled();
  expect(api.calls.employee).toEqual([
    { status: null, page: 0, size: 10 },
    { status: null, page: 1, size: 10 },
  ]);

  await previous.click();
  await expect(rows).toHaveCount(10);
  await expect(page.getByText('Страница 1 из 2', { exact: true })).toBeVisible();
  expect(api.calls.employee.at(-1)).toEqual({ status: null, page: 0, size: 10 });
  await next.click();
  await expect(rows).toHaveCount(2);
  await expect(page.getByText('Страница 2 из 2', { exact: true })).toBeVisible();
  expect(api.calls.employee.at(-1)).toEqual({ status: null, page: 1, size: 10 });

  api.employeeData.PROCESSING[10] = { ...applications.PROCESSING, id: 110, companyName: 'ООО «Обновлённая компания»' };
  const beforeRefresh = api.calls.employee.length;
  await page.getByRole('button', { name: 'Обновить', exact: true }).click();
  await expect(rows.first()).toContainText('ООО «Обновлённая компания»');
  await expect(page.getByText('Страница 2 из 2', { exact: true })).toBeVisible();
  expect(api.calls.employee.slice(beforeRefresh)).toEqual([{ status: null, page: 1, size: 10 }]);

  const beforeFilter = api.calls.employee.length;
  await selectOption(page, 'Статус заявки', 'MANUAL_REVIEW', statusLabels.MANUAL_REVIEW);
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText(applications.MANUAL_REVIEW.companyName!);
  await expect(page.getByText('Найдено заявок: 1', { exact: true })).toBeVisible();
  await expect(page.getByText('Страница 1 из 1', { exact: true })).toBeVisible();
  await expect(previous).toBeDisabled();
  await expect(next).toBeDisabled();
  expect(api.calls.employee.slice(beforeFilter)).toEqual([{ status: 'MANUAL_REVIEW', page: 0, size: 10 }]);

  const beforeAll = api.calls.employee.length;
  await selectOption(page, 'Статус заявки', 'ALL', 'Все');
  await expect(rows).toHaveCount(10);
  await expect(page.getByText('Найдено заявок: 12', { exact: true })).toBeVisible();
  expect(api.calls.employee.slice(beforeAll)).toEqual([{ status: null, page: 0, size: 10 }]);
});

test('an empty employee page shows zero totals and disables both pagination buttons', async ({ page, api }) => {
  for (const status of Object.keys(api.employeeData) as ApplicationStatus[]) api.employeeData[status] = [];
  await openAs(page, api, sessions.bank_employee, '/employee');
  await expect(page.getByText('Заявок с таким статусом пока нет', { exact: true })).toBeVisible();
  await expect(page.getByRole('table')).toHaveCount(0);
  await expect(page.getByText('Найдено заявок: 0', { exact: true })).toBeVisible();
  await expect(page.getByText('Страница 0 из 0', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Назад', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Далее', exact: true })).toBeDisabled();
  expect(api.calls.employee).toEqual([{ status: null, page: 0, size: 10 }]);
});

test('an ALL request failure shows an error without a partial registry and refresh recovers', async ({ page, api }) => {
  let fail = true;
  const employeeReply = api.employeeReply;
  api.employeeReply = (query) => fail
    ? apiError(500, 'Не удалось получить реестр заявок.')
    : employeeReply(query);
  await openAs(page, api, sessions.bank_employee, '/employee');
  await expect(page.getByRole('alert')).toContainText('Не удалось получить реестр заявок.');
  await expect(page.getByRole('table')).toHaveCount(0);
  await expect(page.getByText(applications.PROCESSING.companyName!, { exact: true })).toHaveCount(0);
  await expect(page.getByText(applications.REJECTED.companyName!, { exact: true })).toHaveCount(0);
  await expectSession(page, sessions.bank_employee);
  expect(api.calls.employee).toEqual([{ status: null, page: 0, size: 10 }]);

  fail = false;
  await page.getByRole('button', { name: 'Обновить', exact: true }).click();
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(5);
  await expect(page.getByText('Найдено заявок: 5', { exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(api.calls.employee).toEqual([
    { status: null, page: 0, size: 10 },
    { status: null, page: 0, size: 10 },
  ]);
});

test('all four Core screens are responsive and have no serious or critical axe violations', async ({ page, api }, testInfo) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 390, height: 844 });
  api.history = [applications.APPROVED];

  async function checkScreen(name: string) {
    for (const viewport of [320, 390, 768, 1440]) {
      await page.setViewportSize({ width: viewport, height: 900 });
      // Core swaps desktop/mobile components after matchMedia changes. Measure
      // the settled layout, after React and the browser have painted the swap.
      await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      if (name === 'login') {
        const primary = page.getByRole('button', { name: cards.client_good, exact: true });
        await expect(primary).toHaveClass(/button__component/);
        const theme = await primary.evaluate((element) => ({
          background: getComputedStyle(element).backgroundColor,
          accent: getComputedStyle(document.documentElement).getPropertyValue('--color-light-accent-primary').trim(),
          corporatePrimary: getComputedStyle(document.documentElement).getPropertyValue('--button-primary-base-bg-color').trim(),
        }));
        // Official corp Accent has AA contrast for normal-sized labels. Verify
        // the corporate token override as well as the component's actual color.
        expect(theme).toEqual({ background: 'rgb(33, 33, 36)', accent: '#ef3124', corporatePrimary: '#ef3124' });
      }
      const width = await page.evaluate(() => ({ viewport: window.innerWidth, content: document.documentElement.scrollWidth }));
      expect(width.content, `${name} at ${viewport}px must not overflow`).toBeLessThanOrEqual(width.viewport + 1);
      const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      const serious = result.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical');
      expect(serious, `${name} at ${viewport}px: ${serious.map((violation) => `${violation.id}: ${violation.help}`).join('; ')}`).toEqual([]);
      if (viewport === 390 || viewport === 1440) await page.screenshot({ path: testInfo.outputPath(`${name}-${viewport}.png`), fullPage: true });
    }
  }

  await page.goto('/login');
  await checkScreen('login');
  await page.getByRole('button', { name: cards.client_good, exact: true }).click();
  await expect(page).toHaveURL(/\/client$/);
  await expect(page.getByRole('table').getByText('Одобрено', { exact: true })).toBeVisible();
  await checkScreen('client');

  await page.goto('/employee');
  await expect(page).toHaveURL(/\/403$/);
  await checkScreen('forbidden');
  await action(page, 'Сменить пользователя').click();
  await page.getByRole('button', { name: cards.bank_employee, exact: true }).click();
  await expect(page).toHaveURL(/\/employee$/);
  await selectOption(page, 'Статус заявки', 'ALL', 'Все');
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(5);
  await checkScreen('employee');
});

test('form reports invalid bounds, focuses the field, and accepts inclusive minimums', async ({ page, api }) => {
  api.createReply = (input) => ({ status: 201, body: { ...applications.APPROVED, ...input } });
  await openAs(page, api, sessions.client_good, '/client');
  const amount = page.getByLabel('Сумма кредита, ₽', { exact: true });
  const term = page.getByLabel('Срок, месяцев', { exact: true });
  const purpose = page.getByRole('combobox', { name: 'Цель кредита', exact: true });
  const submit = page.getByRole('button', { name: 'Отправить заявку', exact: true });
  await purpose.focus();
  await page.keyboard.press('ArrowDown');
  await expect(purpose).toHaveValue('EQUIPMENT');
  await page.keyboard.press('ArrowUp');
  await expect(purpose).toHaveValue('WORKING_CAPITAL');
  await amount.fill('99999');
  await term.fill('0');
  await submit.click();
  await expect(amount).toBeFocused();
  await expect(amount).toHaveAttribute('aria-invalid', 'true');
  await expect(term).toHaveAttribute('aria-invalid', 'true');
  expect(api.calls.create).toEqual([]);
  await amount.fill('100000');
  await term.fill('1');
  await submit.click();
  await expect(page.getByText('Одобрено', { exact: true }).first()).toBeVisible();
  expect(api.calls.create).toEqual([{ amount: 100000, termMonths: 1, purpose: 'WORKING_CAPITAL' }]);
});
