#!/usr/bin/env python3
"""Regenerate committed synthetic fixtures; no network, randomness or wall clock."""
import argparse
import copy
import datetime as dt
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AS_OF = dt.date(2026, 10, 23)
MONTHS = [f"{2025 + (9 + n) // 12:04d}-{(9 + n) % 12 + 1:02d}" for n in range(12)]


def flows(value):
    return [{"month": month, "netCreditTurnoverRub": value} for month in MONTHS]


def source(name):
    return {"source": name, "version": "DEMO_DATA_V1", "observedOn": AS_OF.isoformat()}


BASE = {
    "asOf": AS_OF.isoformat(),
    "registeredOn": "2020-01-01",
    "accountOpenedOn": "2022-01-01",
    "directorChangedOn": None,
    "soleFounderChangedOn": None,
    "managementHistoryComplete": True,
    "cashflows": flows(3_000_000),
    "existingDebtPaymentRub": 50_000,
    "bureauScore": 750,
    "maxOverdueDays24m": 0,
    "recentClosedOverdueDays12m": 0,
    "taxBlocked": False,
    "bankrupt": False,
    "liquidating": False,
    "internalSource": source("SYNTHETIC_INTERNAL"),
    "bureauSource": source("SYNTHETIC_BKI"),
    "fnsSource": source("SYNTHETIC_FNS"),
}


def build():
    scenarios = []

    def add(code, title, outcome, path, changes=None, amount=1_000_000, owner="client_good"):
        inputs = copy.deepcopy(BASE)
        inputs.update(changes or {})
        scenarios.append({
            "code": code, "title": title, "owner": owner,
            "amountRub": amount, "termMonths": 12, "purpose": "WORKING_CAPITAL",
            "expectedOutcome": outcome, "expectedPath": path,
            "inputs": inputs,
        })

    add("approved", "Зелёный сценарий", "APPROVED", "Complete fresh inputs, score850, affordable original terms")
    add("low-score", "Низкий демонстрационный балл", "REJECTED", "Score240 below500; no hard-stop required",
        {"accountOpenedOn": "2026-06-01", "cashflows": flows(400_000), "bureauScore": 400}, owner="client_bad")
    add("manual-review", "Серая зона скоринга", "MANUAL_REVIEW", "Score640 requires employee rationale",
        {"accountOpenedOn": "2025-08-01", "cashflows": flows(1_500_000), "bureauScore": 600})
    add("counteroffer", "Доступная меньшая сумма", "OFFER_PROPOSED", "Green score; requested payment exceeds cap; reduced amount >=100000", amount=10_000_000)
    add("no-affordable-offer", "Нет доступной суммы", "REJECTED", "Existing debt600000 exceeds 50% of income1050000",
        {"existingDebtPaymentRub": 600_000})
    add("missing-data", "Отсутствует показатель БКИ", "MANUAL_REVIEW", "Unknown bureauScore stays null; no fabricated score",
        {"bureauScore": None})
    add("stale-data", "Устаревшие сведения БКИ", "MANUAL_REVIEW", "BKI evidence31days old exceeds30day inclusive freshness",
        {"bureauSource": {**source("SYNTHETIC_BKI"), "observedOn": "2026-09-22"}})
    add("tax-block", "Синтетическая блокировка", "REJECTED", "Hard-stop taxBlocked=true", {"taxBlocked": True})
    add("bankrupt", "Синтетическое банкротство", "REJECTED", "Hard-stop bankrupt=true", {"bankrupt": True})
    add("liquidating", "Синтетическая ликвидация", "REJECTED", "Hard-stop liquidating=true", {"liquidating": True})
    add("overdue-91", "Просрочка 91 день", "REJECTED", "Hard-stop maxOverdueDays24m>90", {"maxOverdueDays24m": 91})
    add("young-business", "Возраст бизнеса меньше 6 месяцев", "REJECTED", "Hard-stop before six calendar months",
        {"registeredOn": "2026-05-01", "accountOpenedOn": "2026-05-01"})
    add("director-59-days", "Смена директора 59 дней назад", "MANUAL_REVIEW", "Management change age59<60",
        {"directorChangedOn": (AS_OF - dt.timedelta(days=59)).isoformat()})
    add("founder-59-days", "Смена учредителя 59 дней назад", "MANUAL_REVIEW", "Sole founder change age59<60",
        {"soleFounderChangedOn": (AS_OF - dt.timedelta(days=59)).isoformat()})
    add("short-overdue", "Закрытая короткая просрочка", "MANUAL_REVIEW", "Recent closed overdue30days is a warning",
        {"recentClosedOverdueDays12m": 30, "maxOverdueDays24m": 30})
    add("boundary-business-six-months", "Ровно 6 месяцев бизнеса", "APPROVED", "Registration2026-04-23 plus6months equals valuation date; score730",
        {"registeredOn": "2026-04-23", "accountOpenedOn": "2026-04-23"})
    add("boundary-director-60-days", "Ровно 60 дней после смены", "APPROVED", "Management change age60 is not a soft-stop",
        {"directorChangedOn": (AS_OF - dt.timedelta(days=60)).isoformat()})
    add("boundary-overdue-90", "Просрочка ровно 90 дней", "MANUAL_REVIEW", "90 is not the >90 hard-stop;31..90days conservatively requires review",
        {"maxOverdueDays24m": 90})
    turnover35 = flows(2_000_000)
    turnover35[-2]["netCreditTurnoverRub"] = 2_700_000
    turnover35[-1]["netCreditTurnoverRub"] = 1_300_000
    add("boundary-turnover-drop-35", "Падение оборота ровно 35%", "APPROVED", "Last month1300000 / annual mean2000000;35% does not exceed threshold",
        {"cashflows": turnover35})
    turnover_above35 = copy.deepcopy(turnover35)
    turnover_above35[-1]["netCreditTurnoverRub"] = 1_299_999
    add("turnover-drop-over-35", "Падение оборота больше 35%", "MANUAL_REVIEW", "Last month is below65% of the twelve-month mean",
        {"cashflows": turnover_above35})
    add("boundary-source-30-days", "Сведения БКИ ровно 30 дней", "APPROVED", "Source age30days is accepted by inclusive freshness limit",
        {"bureauSource": {**source("SYNTHETIC_BKI"), "observedOn": "2026-09-23"}})
    return {
        "schemaVersion": "DEMO_INPUTS_V1", "datasetVersion": "DEMO_DATA_V1",
        "asOf": AS_OF.isoformat(), "demoOnly": True,
        "profileScenarios": {"client_good": "approved", "client_bad": "low-score"},
        "scenarios": scenarios,
    }


def rendered(value):
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify committed generated files without changing them")
    args = parser.parse_args()
    dataset = build()
    manifest = {
        "demoOnly": True, "asOf": dataset["asOf"], "ruleVersion": "DEMO_RULES_V1",
        "datasetVersion": dataset["datasetVersion"], "schemaVersion": dataset["schemaVersion"],
        "generator": "python3 scripts/generate-demo-seed.py",
        "notes": "Expected paths use the versioned default demo policy. No live data or actual credit decisions.",
        "scenarios": [{k: v for k, v in scenario.items() if k != "inputs"} for scenario in dataset["scenarios"]],
    }
    outputs = {
        ROOT / "backend/src/main/resources/demo/scenarios-v1.json": rendered(dataset),
        ROOT / "docs/demo-scenarios.json": rendered(manifest),
    }
    for path, text in outputs.items():
        if args.check:
            if not path.exists() or path.read_text(encoding="utf-8") != text:
                raise SystemExit(f"Generated demo fixture differs: {path.relative_to(ROOT)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
    print(f"{'Verified' if args.check else 'Generated'} {len(dataset['scenarios'])} deterministic synthetic scenarios at {AS_OF}")


if __name__ == "__main__":
    main()
