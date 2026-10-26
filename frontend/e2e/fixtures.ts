import { expect, test as base, type Page, type Request, type Route } from '@playwright/test';
import type {
  Application,
  ApplicationDetails,
  ApplicationInput,
  ApplicationStatus,
  PaymentSchedule,
  Session,
  SignApplicationInput,
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
    rejectionReason: 'Текущая долговая нагрузка превышает допустимый уровень.',
    createdAt: '2026-10-22T14:00:00Z',
    updatedAt: '2026-10-22T14:01:00Z',
  },
  MANUAL_REVIEW: {
    id: 44,
    companyName: 'ООО «Вектор»',
    inn: '7705556677',
    amount: 1_200_000,
    termMonths: 18,
    purpose: 'BUSINESS_EXPANSION',
    status: 'MANUAL_REVIEW',
    rejectionReason: null,
    createdAt: '2026-10-22T15:00:00Z',
    updatedAt: '2026-10-22T15:01:00Z',
  },
  SIGNED: {
    id: 45,
    companyName: 'ООО «Маяк»',
    inn: '7706667788',
    amount: 800_000,
    termMonths: 12,
    purpose: 'EQUIPMENT',
    status: 'SIGNED',
    rejectionReason: null,
    createdAt: '2026-10-22T16:00:00Z',
    updatedAt: '2026-10-22T16:05:00Z',
  },
};

export const processingApplication: Application = {
  ...applications.APPROVED,
  status: 'PROCESSING',
  updatedAt: applications.APPROVED.createdAt,
};

export const paymentSchedule: PaymentSchedule = {
  monthlyPayment: 101_542,
  totalInterest: 1_542,
  totalPayout: 101_542,
  items: [{
    monthNumber: 1,
    paymentDate: '2026-11-22',
    totalPayment: 101_542,
    principalPayment: 100_000,
    interestPayment: 1_542,
    remainingDebt: 0,
  }],
};

export const approvedAgreement: Application = {
  ...applications.APPROVED,
  termMonths: 1,
  approvedAmount: 100_000,
  approvedRate: 18.5,
  schedule: paymentSchedule,
};

export const technicalDossier: ApplicationDetails = {
  ...applications.MANUAL_REVIEW,
  reviewReasonCode: 'TECH_FAILURE',
  canRetry: true,
  user: { id: 104, username: 'client_vector', role: 'CLIENT', companyName: applications.MANUAL_REVIEW.companyName, inn: applications.MANUAL_REVIEW.inn },
  fns: { stateStatus: 'ACTIVE', businessAgeMonths: 36, taxBlocksExist: false },
  bki: { bkiScore: 750, monthlyPaymentRub: 0, currentOverdueDays: 0, cntOverdueOver90d: 0, riskBucket: 'CURRENT (0 DPD)' },
  scoring: { scoringScore: 85, calculatedPdnPercent: 0, maxLimitRub: 4_500_000 },
};

export interface ApiReply { status?: number; body: unknown }
interface EmployeeQuery { status: ApplicationStatus | null; page: number; size: number }
interface SignRequest { id: number; input: SignApplicationInput }
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
    APPROVED: [applications.APPROVED],
    REJECTED: [applications.REJECTED],
    MANUAL_REVIEW: [applications.MANUAL_REVIEW],
    SIGNED: [applications.SIGNED],
  };
  calls = {
    login: [] as UserCode[],
    history: 0,
    create: [] as ApplicationInput[],
    polls: [] as number[],
    sign: [] as SignRequest[],
    employee: [] as EmployeeQuery[],
    details: [] as number[],
    retry: [] as number[],
  };
  historyReply: () => ApiReply | Promise<ApiReply> = () => ({ body: this.history });
  createReply: ReplyHandler<ApplicationInput> = () => ({ status: 201, body: processingApplication });
  pollReply: ReplyHandler<number> = () => ({ body: processingApplication });
  signReply: ReplyHandler<SignRequest> = ({ id }) => ({ body: { ...approvedAgreement, id, status: 'SIGNED' } });
  detailsReply: ReplyHandler<number> = (id) => ({ body: { ...technicalDossier, id } });
  retryReply: ReplyHandler<number> = (id) => ({ body: {
    ...applications.MANUAL_REVIEW, id, status: 'PROCESSING', updatedAt: '2026-10-22T15:05:00Z',
  } });
  employeeReply: ReplyHandler<EmployeeQuery> = ({ status, page, size }) => {
    const items = status === null ? Object.values(this.employeeData).flat() : this.employeeData[status];
    return {
      body: {
        items: items.slice(page * size, (page + 1) * size),
        page,
        size,
        totalElements: items.length,
        totalPages: Math.ceil(items.length / size),
      },
    };
  };

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

      const signingPath = /^\/api\/v1\/client\/applications\/(\d+)\/sign$/.exec(path);
      if (signingPath) {
        expect(request.method()).toBe('POST');
        expect(url.search).toBe('');
        expect(request.headers()['content-type']).toContain('application/json');
        const input = request.postDataJSON() as SignApplicationInput;
        expect(Object.keys(input).sort()).toEqual(['confirmTerms', 'smsCode']);
        expect(input.confirmTerms).toBe(true);
        expect(typeof input.smsCode).toBe('string');
        expect(input.smsCode.trim()).not.toBe('');
        const value = { id: Number(signingPath[1]), input };
        this.calls.sign.push(value);
        await this.fulfill(route, await this.signReply(value));
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
        const status = url.searchParams.get('status') as ApplicationStatus | null;
        expect([null, ...Object.keys(applications)]).toContain(status);
        expect([...url.searchParams.keys()].sort()).toEqual(status === null ? ['page', 'size'] : ['page', 'size', 'status']);
        expect(url.searchParams.get('page')).toMatch(/^\d+$/);
        expect(url.searchParams.get('size')).toBe('10');
        const query = { status, page: Number(url.searchParams.get('page')), size: Number(url.searchParams.get('size')) };
        this.calls.employee.push(query);
        await this.fulfill(route, await this.employeeReply(query));
        return;
      }

      const detailsPath = /^\/api\/v1\/employee\/applications\/(\d+)$/.exec(path);
      if (detailsPath) {
        expect(request.method()).toBe('GET');
        expect(url.search).toBe('');
        const id = Number(detailsPath[1]);
        this.calls.details.push(id);
        await this.fulfill(route, await this.detailsReply(id));
        return;
      }

      const retryPath = /^\/api\/v1\/employee\/applications\/(\d+)\/retry$/.exec(path);
      if (retryPath) {
        expect(request.method()).toBe('POST');
        expect(url.search).toBe('');
        expect(request.postData()).toBeNull();
        const id = Number(retryPath[1]);
        this.calls.retry.push(id);
        await this.fulfill(route, await this.retryReply(id));
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
