# Deterministic synthetic dataset — issue 17

The dataset contains 21 explicitly synthetic scenarios at the fixed valuation date **2026-10-23**. It uses the existing `client_good`, `client_bad`, and `bank_employee` logins. No additional authentication enum values, real customer information or live source calls are introduced.

The exact scenario expectations are in [demo-scenarios.json](demo-scenarios.json). They assume `DEMO_RULES_V1`: approval, low-score rejection, each hard stop separately, grey/manual review, an affordable reduced offer, no affordable offer, missing data, stale data and hard/soft boundaries. Expected results are fixture assertions, not actual lending decisions or a claim of regulatory validity.

## Reproduce and load

```bash
python3 scripts/generate-demo-seed.py --check
# Explicitly regenerate the two committed JSON files after a deliberate fixture change:
python3 scripts/generate-demo-seed.py

# From backend/, with the existing local datasource/JWT environment configured:
DEMO_SEED_ENABLED=true ./gradlew bootRun
```

The generator has no randomness, network calls or wall-clock reads. The committed runtime resource is `backend/src/main/resources/demo/scenarios-v1.json`; the concise review manifest is generated alongside it. `DemoSeedCatalog` does not depend on the risk engine, so this package remains independently buildable after issue14.

Seeding is opt-in. `DemoDataSeeder` inserts the default synthetic internal/BKI/FNS profile rows for the two clients, then inserts stable-keyed `PROCESSING` applications, immutable input snapshots, creation audit and durable job rows in transactions. The later issue16 processor computes the outcomes. A fixed timestamp and fixture order make fresh input/evidence snapshots reproducible.

Rerunning the seed **does not reset a decision**, replace an immutable snapshot, mutate existing source profile values, recreate users or append duplicate audit. `demo-v1:<scenario-code>` is the stable application key. A scenario's data lives in its own immutable snapshot; switching scenarios does not race on a shared client profile. Later ordinary client submissions capture the default profile through the mock data-provider adapters.

`client_good` owns the green, manual, offer and boundary scenarios; `client_bad` owns the low-score scenario and its default profile. This permits real owner/other-client checks without changing login codes. Every source has a synthetic source marker, format version and observation date. The stale example is deliberately 31 days old for a 30-day limit; null bureau score remains absent.

These seeds have no real financial effect. Accepting a later counteroffer only changes the demonstration application's state. There is no signing, disbursement, SMS, credit-bureau or tax-service traffic.

## Checks

`DemoSeedCatalogTest` verifies fixed valuation date, distinct identities and required outcome paths. Real PostgreSQL `DemoDataSeederTest` verifies two fresh schemas have identical snapshot hashes and creation times, then repeats the seed after an application decision and checks identities/evidence/audit are unchanged. With the issue16 engine available, the same scenarios must be checked against actual engine outputs rather than trusting the manifest.

Set `TEST_DATABASE_URL` and `REQUIRE_POSTGRES_TESTS=true` when running the database tests; see [data-dictionary.md](data-dictionary.md). Consult the delivered verification report for which checks were actually executed.
