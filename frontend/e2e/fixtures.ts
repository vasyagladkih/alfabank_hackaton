import { expect, test as base, type Page, type Request, type Route } from '@playwright/test';
import type {
  Application,
  ApplicationInput,
  ApplicationStatus,
  Session,
  UserCode,
} from '../src/api/types';

// Deliberately test-only: production always uses the real HTTP API.
export const sessions: Record<UserCode, Session> = {
  client_good: {
    token: 'e2e-client-good-token',
    user: { id: 101, username: 'client_good', role: 'CLIENT', companyName: 'ООО «Ромашка»', inn: '7701234567' },
  },
  client_bad: {
    token: 'e2e-client-bad-token',
    user: { id: 102, username: 'client_bad', role: 'CLIENT', companyName: 'ООО «Высокий риск»', inn: '7709876543' },
  },
  bank_employee: {
    token: 'e2e-employee-token',
    user: { id: 201, username: 'Анна Смирнова', role: 'EMPLOYEE' },
  },
};

export const applications: Record<ApplicationStatus, Application> = {
  PROCESSING: {
    id: 41,
    companyName: 'ООО «Горизонт»',
    inn: '7701112233',
    amount: 900_000,
    termMonths: 6,
    purpose: 'WORKING_CAPITAL',
    status: 'PROCESSING',
    version: 0,
    rejectionReason: null,
    createdAt: '2026-10-22T12:00:00Z',
    updatedAt: '2026-10-22T12:00:00Z',
  },
  APPROVED: {
    id: 42,
    companyName: 'ООО «Ромашка»',
    inn: '7701234567',
    amount: 1_500_000,
    termMonths: 12,
    purpose: 'EQUIPMENT',
    status: 'APPROVED',
    version: 1,
    rejectionReason: null,
    createdAt: '2026-10-22T13:00:00Z',
    updatedAt: '2026-10-22T14:00:00Z',
  },
  REJECTED: {
    id: 43,
    companyName: 'ООО «Старт»',
    inn: '7704445566',
    amount: 2_000_000,
    termMonths: 24,
    purpose: 'REFINANCING',
    status: 'REJECTED',
    version: 1,
    rejectionReason: 'Текущая долговая нагрузка превышает допустимый уровень.',
    createdAt: '2026-10-22T14:00:00Z',
    updatedAt: '2026-10-22T14:01:00Z',
  },
  MANUAL_REVIEW: {
    id: 44, companyName: 'ООО «Демо рассмотрение»', inn: '0000000044',
    amount: 1500000, termMonths: 12, purpose: 'WORKING_CAPITAL',
    status: 'MANUAL_REVIEW', version: 1,
    createdAt: '2026-10-22T14:30:00Z', updatedAt: '2026-10-22T14:30:01Z',
    decisionSummary: { ruleVersion: 'demo-v1', assessedAt: '2026-10-22T14:30:01Z', explanation: 'Требуется рассмотрение сотрудником.', demoOnly: true },
  },
  OFFER_PROPOSED: {
    id: 45, companyName: 'ООО «Демо предложение»', inn: '0000000045',
    amount: 1500000, termMonths: 12, purpose: 'EQUIPMENT',
    status: 'OFFER_PROPOSED', version: 2,
    offer: { amount: 900000, termMonths: 12, annualRate: '18.50', monthlyPayment: '82724.12' },
    createdAt: '2026-10-22T14:35:00Z', updatedAt: '2026-10-22T14:35:01Z',
    decisionSummary: { ruleVersion: 'demo-v1', assessedAt: '2026-10-22T14:35:01Z', explanation: 'Предложена меньшая сумма.', demoOnly: true },
  },
};

export const processingApplication: Application = {
  ...applications.APPROVED,
  status: 'PROCESSING',
  version: 0,
  updatedAt: applications.APPROVED.createdAt,
};

export interface ApiReply { status?: number; body: unknown }
type ReplyHandler<T> = (value: T) => ApiReply | Promise<ApiReply>;

export function apiError(status: number, message: string): ApiReply {
  return {
    status,
    body: { code: `HTTP_${status}`, message, timestamp: '2026-10-22T14:30:00Z' },
  };
}

export class TestApi {
  session: Session = sessions.client_good;
  history: Application[] = [];
  employeeData: Record<ApplicationStatus, Application[]> = {
    PROCESSING: [applications.PROCESSING],
    MANUAL_REVIEW: [],
    OFFER_PROPOSED: [],
    APPROVED: [applications.APPROVED],
    REJECTED: [applications.REJECTED],
  };
  calls = {
    login: [] as UserCode[],
    history: 0,
    create: [] as ApplicationInput[],
    polls: [] as number[],
    employee: [] as ApplicationStatus[],
  };
  historyReply: () => ApiReply | Promise<ApiReply> = () => ({ body: this.history });
  createReply: ReplyHandler<ApplicationInput> = () => ({ status: 201, body: processingApplication });
  pollReply: ReplyHandler<number> = () => ({ body: processingApplication });
  employeeReply: ReplyHandler<ApplicationStatus> = (status) => ({ body: this.employeeData[status] });

  private async fulfill(route: Route, reply: ApiReply) {
    await route.fulfill({ status: reply.status ?? 200, json: reply.body });
  }

  private assertAuthenticated(request: Request) {
    expect(request.headers().authorization, `${request.method()} ${request.url()}`).toBe(`Bearer ${this.session.token}`);
  }

  async install(page: Page) {
    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname;

      if (path === '/api/v1/auth/demo-login') {
        expect(request.method()).toBe('POST');
        expect(url.search).toBe('');
        expect(request.headers().authorization).toBeUndefined();
        expect(request.headers()['content-type']).toContain('application/json');
        const body = request.postDataJSON() as { userCode: UserCode };
        expect(Object.keys(body)).toEqual(['userCode']);
        expect(Object.keys(sessions)).toContain(body.userCode);
        this.calls.login.push(body.userCode);
        this.session = sessions[body.userCode];
        await this.fulfill(route, { body: this.session });
        return;
      }

      this.assertAuthenticated(request);
      if (path === '/api/v1/client/applications') {
        expect(url.search).toBe('');
        if (request.method() === 'GET') {
          this.calls.history += 1;
          await this.fulfill(route, await this.historyReply());
          return;
        }
        expect(request.method()).toBe('POST');
        expect(request.headers()['content-type']).toContain('application/json');
        const body = request.postDataJSON() as ApplicationInput;
        this.calls.create.push(body);
        await this.fulfill(route, await this.createReply(body));
        return;
      }

      const applicationPath = /^\/api\/v1\/client\/applications\/(\d+)$/.exec(path);
      if (applicationPath) {
        expect(request.method()).toBe('GET');
        expect(url.search).toBe('');
        const id = Number(applicationPath[1]);
        this.calls.polls.push(id);
        await this.fulfill(route, await this.pollReply(id));
        return;
      }

      if (path === '/api/v1/employee/applications') {
        expect(request.method()).toBe('GET');
        const status = url.searchParams.get('status') as ApplicationStatus;
        // Omitting status means PROCESSING in the contract; ALL is never an API enum.
        expect(['PROCESSING', 'MANUAL_REVIEW', 'OFFER_PROPOSED', 'APPROVED', 'REJECTED']).toContain(status);
        expect([...url.searchParams.keys()]).toEqual(['status']);
        this.calls.employee.push(status);
        await this.fulfill(route, await this.employeeReply(status));
        return;
      }

      await route.fulfill({ status: 501, json: { message: `Unexpected test API route: ${path}` } });
      throw new Error(`Unexpected API request: ${request.method()} ${request.url()}`);
    });
  }
}

export const test = base.extend<{ api: TestApi }>({
  api: async ({ page }, use) => {
    const api = new TestApi();
    await api.install(page);
    await use(api);
  },
});

export { expect };

export async function openAs(page: Page, api: TestApi, session: Session, path: string) {
  api.session = session;
  await page.goto('/login');
  await page.evaluate((value) => {
    localStorage.setItem('auth_token', value.token);
    localStorage.setItem('auth_user', JSON.stringify(value.user));
  }, session);
  await page.goto(path);
}

export async function storedSession(page: Page) {
  return page.evaluate(() => ({
    token: localStorage.getItem('auth_token'),
    user: localStorage.getItem('auth_user'),
  }));
}

export async function expectSession(page: Page, session: Session | null) {
  const stored = await storedSession(page);
  expect(stored.token).toBe(session?.token ?? null);
  expect(stored.user === null ? null : JSON.parse(stored.user)).toEqual(session?.user ?? null);
}

export async function selectOption(page: Page, label: string, value: string, visibleName: string | RegExp) {
  const control = page.getByLabel(label, { exact: true });
  if (await control.evaluate((element) => element instanceof HTMLSelectElement)) {
    await control.selectOption(value);
    return;
  }
  await control.click();
  await page.getByRole('option', { name: visibleName, exact: typeof visibleName === 'string' }).click();
}

export function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => { resolve = done; });
  return { promise, resolve };
}
