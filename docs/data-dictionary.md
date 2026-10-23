# Synthetic scoring data foundation — issue 14

This is a hackathon demonstrator. Every added business source is synthetic. No provider calls a bank, credit bureau, tax service or any live financial data source. Policy thresholds are configurable demonstration assumptions, not regulatory requirements or validated predictions.

## Migration and preserved data

Flyway owns startup migrations. `spring.sql.init.mode=never` prevents a second bootstrap path. V1 creates the original `users` and `applications` only if absent, adds columns/tables/indexes, and inserts the original three login codes only if missing. Existing IDs, identities, company names, request amounts, timestamps and statuses are retained.

An existing nonempty database without Flyway history is baselined at **version 0**, then V1 runs. Do not change this to the Flyway default version 1: that would skip the required additive migration. Flyway checksums detect later edits; subsequent changes require a new migration. Repeated startup does not rerun V1.

The original `schema.sql` and `data.sql` remain as documented legacy fixtures for upgrade tests; runtime initialization does not execute them. Request amount/term/status/purpose constraints are added `NOT VALID` so externally-written legacy rows are not destroyed or silently corrected; PostgreSQL still checks new and updated rows. New columns and all new tables have immediately enforced constraints.

Existing `PROCESSING` applications receive durable job rows. No historical snapshot is fabricated: a later recovery worker must capture missing legacy evidence once, mark `origin=LEGACY_RECOVERY`, and state the actual capture time. Existing terminal applications can legitimately have no scoring evidence.

## Mutable synthetic source tables

All monetary values use `NUMERIC(18,2)` RUB. Null means **unknown**, including for booleans and debts; zero and false mean known values. No default converts unknown facts into a good result. Source timestamps are dates because fixture freshness is evaluated by complete days.

| Table / field | Meaning and units | Source and use |
|---|---|---|
| `company_profiles.user_id` | FK to existing client identity | No duplicate login system |
| `business_registered_on` | Registration date | Business age in complete calendar months |
| `account_opened_on` | Account opening date | Account age score factor |
| `director_changed_on`, `sole_founder_changed_on` | Most recent changes, dates | Management recency warnings |
| `management_history_complete` | Whether absence of a change date is known | Null dates mean no event only when this flag is true |
| `internal_monthly_debt_rub` | Existing monthly debt service in synthetic internal data | Sum with known external debt; an unknown component makes the total unknown |
| `monthly_cashflows.month` | First day of a closed calendar month | Unique per user/month; twelve consecutive closed months are required by the planned engine |
| `turnover_rub` | Net monthly credit turnover, RUB, internal transfers already removed | Six-month income/score/stability and twelve-month decline comparison |
| `synthetic_bureau_facts.score` | Synthetic bureau indicator, 0–1000 | Scorecard input; not a calibrated probability |
| `monthly_debt_rub` | External monthly debt service, RUB | Affordability input |
| `max_overdue_days` | Maximum overdue duration in preceding 24 months | Hard-stop boundary |
| `recent_closed_overdue_days` | Largest recently closed overdue duration in preceding 12 months | Warning input |
| `synthetic_registry_facts.tax_blocked` | Synthetic account-block fact | Hard-stop input |
| `bankrupt`, `liquidating` | Synthetic registry facts | Hard-stop inputs |
| `source` | Constrained `SYNTHETIC_INTERNAL`, `SYNTHETIC_BKI` or `SYNTHETIC_FNS` | Explicit demo marker on every source row |
| `source_version` | Fixture/data format version | Reproducibility and provenance |
| `as_of` | Date through which the source is known | Freshness evaluated against the immutable snapshot valuation date |

The issue16 provider interfaces consume these tables through `SyntheticDataRepository`. They are replaceable adapters; the rule engine does not know JDBC or HTTP. A future real adapter must be explicitly implemented and configured; this project contains none.

## Immutable evidence and operation state

| Table / columns | Contract |
|---|---|
| `application_snapshots.application_id` | Exactly one immutable input document per application |
| `schema_version`, `as_of`, `captured_at`, `origin` | Payload format, fixed valuation date, real capture instant, and `DEMO_PROFILE` / `DEMO_SCENARIO` / `LEGACY_RECOVERY` provenance |
| `payload_json`, `payload_sha256` | Full copied inputs, canonical decimal JSON and SHA-256; later source changes cannot affect the input; hash checked on repository reads |
| `scoring_results` | One immutable outcome document, rule version and decision instant per application |
| `application_audit` | Append-only actor/event/transition/version/rationale/evidence; `(application_id,event_key)` rejects duplicate automatic events |
| `mutation_requests` | Immutable response plus normalized command hash for an actor/application/request UUID; enables exact replay without duplicate decisions |
| `processing_jobs` | Durable attempts, next eligible time, completion and safe error code; no in-memory queue or distributed lease |
| `applications.version` | Starts at zero; each real workflow transition increments it |
| `offered_*` | Offered terms separate from unchanged original amount/term; amount cannot exceed request |
| `demo_scenario_key` | Unique, optional fixture identity; supports reruns without resetting decisions |

PostgreSQL triggers reject UPDATE and DELETE of snapshots, scoring results, audit and mutation receipts. Repository methods only insert/read them. Ordinary application transitions remain mutable and must be transactionally coordinated by workflow services. Database owners can of course alter database schema; this is evidence integrity for the demonstrator, not a regulated tamper-proof archive.

The snapshot payload contract is versioned as `DEMO_INPUTS_V1`. It contains `inputs` (the pure risk module's `RiskInputs`) and a copied `request`, plus explicit demo identity/scenario metadata. Dates use ISO `YYYY-MM-DD`, cashflow months `YYYY-MM`, decimals are JSON numbers, and unknown values are preserved as null. Source records carry `{source,version,observedOn}`. Policy consumes a fixed valuation date so retrying an application does not age its evidence invisibly.

## Freshness and missing data

The intended demo rules accept BKI/FNS evidence through age 30 days and internal evidence through age 45 days, inclusive. Future observations, incomplete cashflow windows, missing source records, unknown required facts and unverified management history require explicit data-quality handling, never automatic default values. Known hard-stop facts retain the policy's documented precedence. These assumptions are implemented and boundary-tested by issue16, not asserted as live banking policy here.

## PostgreSQL checks

Use a disposable **test database** whose user may create/drop isolated schemas. Each test creates a unique schema and removes only that schema. Example environment (local demonstration defaults):

```bash
TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/alfadb \
TEST_DATABASE_USER=postgres TEST_DATABASE_PASSWORD=postgres \
REQUIRE_POSTGRES_TESTS=true ./gradlew test
```

Without `TEST_DATABASE_URL`, PostgreSQL tests are explicitly skipped. Release/integration checks must set `REQUIRE_POSTGRES_TESTS=true`; a missing connection setting then fails instead of silently skipping. Tests cover fresh bootstrap, populated baseline upgrade, restart idempotency, decimal/null fidelity, source uniqueness, immutable evidence and duplicate event rejection. Read the delivered verification report for actual execution outcomes; presence of a test is not evidence it passed.

Dependencies added: Spring Boot Flyway starter and Flyway PostgreSQL database support (Apache-2.0, versions managed by the existing Spring Boot dependency platform). The existing Java/Spring/PostgreSQL versions are unchanged.
