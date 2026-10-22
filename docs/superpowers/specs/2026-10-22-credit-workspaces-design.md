# Issue #11 — кредитные кабинеты

Source: https://github.com/vasyagladkih/alfabank_hackaton/issues/11 and the user's full requirements, 2026-10-22.
Baseline: `191108c233ec9981e144f06a2e6b79801235900b`; contract blob `74f4a01614a7d656491a971b78d852329c6fee3c`.

## Scope and decisions

Implement an independent React/TypeScript/Vite SPA in `frontend/`. The baseline has no frontend, other branches or open PRs. Keep Java backend and OpenAPI contract intact. Backend controllers currently contain literal `...` placeholders; a live backend integration cannot be claimed.

The user authorized implementation, a feature branch, and a review PR, with no merge, main push, force push or deployment. Implementation is performed in the current session. Supporting agents may inspect the contract, tests and final diff.

## Architecture

- Generate TypeScript definitions from `docs/openapi.yaml` using openapi-typescript; check generated output for drift.
- A single API client sends real HTTP requests, attaches Bearer authentication except for demo-login, and distinguishes 401 from 403. Aborted/stale requests cannot change the next session.
- Store the token under `auth_token` and the returned profile under `auth_user`. Restore a valid pair on refresh. There is no current-user endpoint in this contract. The stored role controls navigation only; backend authorization remains authoritative.
- Browser routes: `/` redirects to `/login`; `/client` and `/employee` require their respective roles. Missing session redirects to `/login`. Wrong role and API 403 show `/403` without deleting the session. API 401 expires the session.
- Logout and cross-tab session changes cancel requests and polling. Login persists the server's profile and redirects by its role.

## Screens

1. Login: three prescribed role cards (`client_good`, `client_bad`, `bank_employee`), submitting/failed states, duplicate-click protection.
2. Client: company and INN header; amount integer 100000–50000000 RUB, term integer 1–60 months, four contract purposes; POST followed by a cancellable serial polling loop with a 2000 ms interval; approved/rejected banners with rejection reason; history with loading/error/empty states. Resume processing requests after refresh. Never automatically retry a POST.
3. Employee: username/role header; full application table; status filter and manual refresh. `ALL` is a frontend-only filter: fetch all three statuses explicitly, deduplicate by ID, prefer the newest version, sort newest first. A partial failure must not masquerade as a complete list.
4. Forbidden: exact heading «403 — Доступ ограничен», role-aware explanation, own-workspace link and switch-user action.

Responsive Russian UI, semantic controls/table captions, focus indicators, associated validation messages, screen-reader status announcements, reduced-motion support. Test fixtures are excluded from production.

## Verification

- API/session tests: auth headers, all demo codes, 401 vs 403, malformed/partial storage, refresh, cross-session stale responses, ALL aggregation/error/abort.
- Form tests: integer bounds, all four purposes, invalid and empty values, duplicate submit.
- Polling tests: 2000 ms schedule, terminal states, transient error recovery, permanent errors, cancellation, no overlapping requests.
- UI and Chromium tests: all routes, role switching, refresh, history, submit/poll status, employee filters, mobile layout and accessibility.
- Run type generation drift check, lint, TypeScript, unit/component tests, production build and browser tests. Browser API fixtures are expressly test-only; report their boundary honestly.

## Final design-system integration

Official Alfa Core Components are used through pinned separate packages and the compiled corporate theme `@alfalab/core-components-themes/corp.css`. Theme tokens are imported after component defaults. React 17.0.2 satisfies the complete dependency graph, including the React 16/17 peer requirement of transitive react-virtual 2.10.4. System typography is explicitly supported by Core; proprietary font assets are excluded.

Actual browser checks exposed ARIA defects in Select 19.2.11. The confirmed integration uses public FormControl 14.0.11 with a native select for purpose and status. Core owns field/label/error appearance; the native element owns accessible naming, semantics and keyboard selection. Core Button's corporate `accent` variant provides sufficient contrast for normal-size labels; colored statuses use the official primary text token. No axe rules are disabled and no private component DOM is patched.

Final verification and all source/coverage boundaries are recorded in `frontend/VERIFICATION.md`: 86 unit/component tests and 16 Chromium scenarios passed. The dependency audit retains two moderate React Router findings whose described entry points are not used by the current fixed-route, client-rendered application. Their upstream fix requires a React major incompatible with the selected Core dependency graph; this limitation is explicit in the review PR.
