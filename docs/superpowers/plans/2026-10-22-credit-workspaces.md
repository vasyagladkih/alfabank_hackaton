# Credit Workspaces Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline; use a separate reviewer for the final diff.

**Goal:** Implement all four screens and acceptance criteria of issue #11, then open a review PR.

**Architecture:** A typed HTTP client and persistent auth provider own API/session behavior. Feature components own forms and list state; a serial abortable polling utility owns status requests. React Router enforces role navigation.

**Tech Stack:** React, TypeScript, Vite, React Router, openapi-typescript, Vitest, Testing Library, Playwright.

**Spec:** `docs/superpowers/specs/2026-10-22-credit-workspaces-design.md`.

## Global Constraints

- `auth_token` and `auth_user`; no Bearer header on demo-login.
- CLIENT `/client`, EMPLOYEE `/employee`; wrong role `/403` preserves session, 401 clears it.
- Integer amount 100000–50000000, term 1–60, contract purpose enum.
- Poll after 2000 ms, stop for final status, cancel on unmount/session change; never overlap requests.
- ALL fetches PROCESSING/APPROVED/REJECTED explicitly; no fabricated production data.
- Preserve backend and contract; no merge, main push, force push or deployment.

## Review Focus

- A response from an old session arriving after logout/login must not redirect or expose old data.
- Storage access denied/corrupt/partial session must fail safely with a usable sign-in screen.
- Slow polling requests and transient network errors must not create overlapping requests or lose a pending application.
- A failed member of ALL aggregation must not show a misleading partial registry.
- Invalid inputs and repeated submit must not create extra applications.

### Task 1: API and session foundation

**Files:** `frontend/package.json`, config files, `src/api/*`, `src/auth/session.ts`, unit tests.
**Interfaces:** generated `components['schemas']`; `createApiClient({ token, onAuthError })`; `readSession`, `saveSession`, `clearSession`.

- [x] Configure reproducible local tooling and generate schema types.
- [x] Write tests for headers, 401/403, status aggregation and persisted-session validation; run and observe failures.
- [x] Implement the HTTP client and session helpers; make tests pass.
- [x] Keep the verified foundation in the consolidated issue #11 change.

### Task 2: Authentication and routing

**Files:** `src/auth/AuthProvider.tsx`, `src/App.tsx`, `src/pages/LoginPage.tsx`, `src/pages/ForbiddenPage.tsx`, shared layout and styles, route tests.
**Interfaces:** `useAuth()` exposes session, api, login and logout; role guards redirect without changing a valid session.

- [x] Add tests for unauthenticated and cross-role routes, login codes, refresh, expired session and sign-out.
- [x] Observe missing behavior, implement the provider, guards, login and forbidden screens.
- [x] Verify session-sensitive cancellation, storage errors, and all routing tests.
- [x] Keep the authenticated shell in the same issue #11 change.

### Task 3: Client and employee workflows

**Files:** `src/features/applications/*`, `src/pages/ClientPage.tsx`, `src/pages/EmployeePage.tsx`, tests.
**Interfaces:** application request/response from generated schema; `pollApplication` with callback and AbortSignal; `validateApplication`.

- [x] Add failing validation and polling tests with deterministic timers, including cancellation/errors/no overlap.
- [x] Implement form, serial polling, processing recovery, result banners and history.
- [x] Implement employee filtering/refresh with loading/error/empty states and cancellation.
- [x] Run feature/component tests; keep the completed workflows in the same issue #11 change.

### Task 4: Browser verification and handoff

**Files:** `e2e/*`, Playwright config, frontend README, repository README, `.github/workflows/frontend.yml`.
**Interfaces:** same real application bundle and documented API endpoints; browser fixtures only in tests.

- [x] Execute browser scenarios for all roles, terminal polling, error/cancel, filters, mobile and accessibility.
- [x] Run `npm run check`, `npm run test:e2e` and inspect actual results.
- [x] Obtain independent code review; fix material findings with regression tests.
- [ ] Recheck remote main/PRs, publish feature branch and open a PR without merging/deploying. Publication is the next step after this local verification snapshot.
- [x] Record actual commands/results, acceptance matrix and backend integration limitation in frontend/VERIFICATION.md; include them in the PR and handoff.

## Verification snapshot

The final clean install passed; 86 unit/component tests and all 16 Chromium scenarios passed. Independent review findings were fixed and rechecked. Public Core FormControl with a native select resolves the observed Select ARIA defects. Dependency audit findings and live-backend limitations remain explicit in frontend/VERIFICATION.md. Stage checkpoints are combined into one reviewable issue #11 commit; no intermediate commit sequence is claimed.
