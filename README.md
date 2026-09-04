# Network Marketing Planner

Open-source **Android** planner for network marketers. Map a current organization, sketch an ideal structure, and estimate what PV/BV-style volume it takes to reach an income or rank goal.

This is a **local-first** v1: data stays on the device (Room). There is no account system and no company API.

**Display name:** Network Marketing Planner  
**Application ID:** `com.networkmarketing.planner`  
**License:** [Apache License 2.0](LICENSE)

## Unofficial tool

This project is **not affiliated with, endorsed by, or sponsored by** any network marketing company. UI copy is generic (organization, frontline, volume, rank). The default engine is an **unofficial planning profile** (`AmwayNA_PY2027`) that encodes North America Core Plan / Core Plus tables for Performance Year 2027 (updated September 2026). It is **not** a company publication, tax advice, or an income claim. **Income is not guaranteed.** Actual payouts, pin ranks, and rules vary by market and change over time.

## What v1 does

| Tab | Purpose |
| --- | --- |
| **Map** | Current organization tree/chart with personal, Group (pass-up), Ruby/Side, and team PV. Sample team is seeded on first launch. |
| **Plan** | Ideal/target structure. Compare current vs ideal and see gap suggestions, including Silver Producer Q-month paths. |
| **Calculator** | PV/BV inputs (or “use current org”), Core Plan payout lines, Plus/Elite, Rule 4.12 factor, rank progress. |
| **Goals** | Income/rank goals, retail 10/15/20%, Rule 4.12 / VCS, YTD Q/PQ counters, bonus toggles, full assumption list. |

Onboarding asks you to accept the unofficial-tool disclaimer and set a first income/rank goal.

## Domain model

Extensible types live under `app/src/main/java/com/networkmarketing/planner/domain`:

- `Member` — a person
- `OrgNode` — that person placed in `CURRENT` or `IDEAL` with monthly personal PV/BV
- `OrgSnapshot` — members + nodes; **team volume** is personal + all descendants
- `UserGoals` / `PlannerSettings` — income/rank targets, Rule 4.12, YTD pin counters
- `CompensationConfig` (`AmwayNA_PY2027`) + `CompensationEngine` + `LeadershipBonus` — **the only place payout math should change**

Persistence is Room (`data/local`) behind `PlannerRepository`.

## Compensation profile: `AmwayNA_PY2027`

Rates live in `AmwayNaPy2027.kt`. Formulas live in `CompensationEngine.kt` and `LeadershipBonus.kt`.

### Core Plan (monthly)

1. **BV:PV = 3.43** as of 1 September 2026 (editable in Goals).
2. **Performance Bonus Schedule** (Group PV → % of BV): 100–299.99 → 3%, 300–599.99 → 6%, 600–999.99 → 9%, 1,000–1,499.99 → 12%, 1,500–2,499.99 → 15%, 2,500–3,999.99 → 18%, 4,000–5,999.99 → 21%, 6,000–7,499.99 → 23%, 7,500+ → 25%.
3. **Group Volume** = Personal Volume + pass-up from in-market legs that are **not** at 25% this month. Qualified 25% legs are excluded. You are paid at least the highest frontline %.
4. **Ruby / Side Volume** = Personal + pass-up from legs not at 25% (and not a qualified Platinum). Used for Ruby Bonus and Plus/Elite.
5. **Personal Performance Bonus** = schedule% × Personal BV after **Rule 4.12**. Full BV needs ≥70% of Personal Volume from customer sales and ≥60% VCS; otherwise Personal BV is prorated (`min(customer/0.70, vcs/0.60)`). Toggles are on Goals.
6. **Differential Bonus** = `(your% − frontline%) × that frontline’s Group BV` when yours is higher. Rule 4.13 is a Goals toggle.
7. **Silver Producer / Q month:** 7,500 Group PV, **or** 2,500 Group PV + one 25% leg, **or** two 25% legs in the same month.
8. **Ruby Bonus:** ≥15,000 Ruby PV → +2% of Ruby BV.
9. **Leadership Bonus:** one 25% group plus ≥2,500 PV outside that group, **or** two 25% groups. Bottom-up 6% with Published LBA = 6% × 7,500 × BV:PV (**\$1,543.50** at 3.43). Unit tests cover BRG examples B–E (those pages used BV:PV 3.00 so LBA = \$1,350). Intermediates under 25% contribute 6% of their BV and keep none.
10. **Depth Bonus:** 3+ in-market 25% legs and at least one of those has a 25% downline. Planner pays 1% of those depth legs’ Group BV. **Simplification:** MDA (\$257.25 at 3.43) and further upline splits are omitted.
11. **Retail margin:** selectable 10% / 15% / 20% of customer-sales BV. Typical published retail markup is ~10%; 15/20 are planner what-ifs.

### Core Plus (monthly where documented; annual as progress)

- **Baseline** for discretionary incentives: 150 Personal PV/month (1,800/year) and 60% VCS.
- **Performance Plus:** 7,500–12,499.99 Ruby PV → 2% of Ruby BV. **Performance Elite:** ≥12,500 Ruby PV → 4%. Highest multiplier wins; Core Plan Ruby Bonus can stack.
- **CSI:** new-IBO years, ≤9% bracket, `(10% − your%) × VCS BV`, cap \$75/month (toggle).
- **BFI / BBI / Bronze pin:** 30% / 40% multipliers on Performance Bonus when month rules and baseline are met (eligibility toggles). SSI dollar table is not encoded; Goals stores new-IBO baseline months.
- **PQ (Platinum+):** 7,500 Ruby PV **or** 4,000 Ruby PV + a 25% leg. Annual table: 6–11 PQ → \$6,000; 12 PQ → \$18,000; 12 PQ + 90,000 Ruby PV → \$20,000 (shown from YTD inputs, not folded into monthly estimated payout).
- **FQ:** one per 25% frontline per month (max 12 per leg per PY).
- **TTCI:** Platinum first-time ≥6 Q months in 12 rolling with 3 consecutive; requal ≥6 in the PY. Founders Platinum: 12 Q months (VE: 10–11 with 90k Group PV or 108k Total Downline PV). Documented first/second year amounts \$1,500/\$3,500 and \$2,500/\$7,500 — confirm against the current PY table.
- **FSI:** progress follows Founders Platinum / VE; payout table not encoded.
- **Emerald / Diamond:** pin progress (3 / 6 Silver Producer legs for six months). Profit-sharing schedules are **not** encoded.

## Build and run

Requirements: **JDK 17+** (21 is fine), **Android SDK 35**, Android Studio (or command-line SDK).

```bash
./gradlew assembleDebug
```

Install on a device/emulator:

```bash
./gradlew installDebug
```

Open the project in Android Studio (File → Open the repo root), let Gradle sync, and run the `app` configuration.

Debug APK path: `app/build/outputs/apk/debug/app-debug.apk`

Unit tests for the engine (no emulator):

```bash
./gradlew testDebugUnitTest
```

If `ANDROID_HOME` / `ANDROID_SDK_ROOT` is unset, create `local.properties` with:

```
sdk.dir=/path/to/Android/sdk
```

(`local.properties` is gitignored.)

## Out of scope (v1)

Company APIs, iOS, accounts/cloud sync, and Play Store publishing.

## License

Copyright 2026 Network Marketing Planner contributors.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

