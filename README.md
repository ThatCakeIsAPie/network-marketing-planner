# Network Marketing Planner

Open-source **Android** planner for network marketers. Map a current organization, sketch an ideal structure, and estimate what PV/BV-style volume it takes to reach an income or rank goal.

This is a **local-first** v1: data stays on the device (Room). There is no account system and no company API.

**Display name:** Network Marketing Planner  
**Application ID:** `com.networkmarketing.planner`  
**License:** [Apache License 2.0](LICENSE)

## Unofficial tool

This project is **not affiliated with, endorsed by, or sponsored by** any network marketing company. UI copy is generic (organization, frontline, volume, rank). The default math is an **unofficial, simplified** model inspired by common North American PV/BV performance-bonus plans so the calculator is useful out of the box. It is **not** a published compensation plan, tax advice, or an income claim. **Income is not guaranteed.** Actual payouts, pin ranks, and rules vary by market and change over time.

## What v1 does

| Tab | Purpose |
| --- | --- |
| **Map** | Current organization tree/chart with personal and group PV/BV. Sample team is seeded on first launch. Tap a person to edit volume or add frontline. |
| **Plan** | Ideal/target structure. Compare current vs ideal (volume, people, estimated payout) and see gap suggestions. |
| **Calculator** | PV/BV inputs (or “use current org”), estimated monthly payout, rank progress. |
| **Goals** | Income and rank goals, BV/PV ratio, which bonus lines to include, full assumption list, restore demo data. |

Onboarding asks you to accept the unofficial-tool disclaimer and set a first income/rank goal.

## Domain model

Extensible types live under `app/src/main/java/com/networkmarketing/planner/domain`:

- `Member` — a person
- `OrgNode` — that person placed in `CURRENT` or `IDEAL` with monthly personal PV/BV
- `OrgSnapshot` — members + nodes; **group volume** is personal + all descendants
- `UserGoals` / `PlannerSettings` — income/rank targets and formula knobs
- `CompensationConfig` + `CompensationEngine` — **the only place payout math should change**

Persistence is Room (`data/local`) behind `PlannerRepository`.

## Default compensation assumptions

All of these are documented in-app (Goals) and encoded in `DefaultCompensation.US_STYLE`:

1. **Single-month snapshot.** Real pin ranks often need several qualifying months.
2. **Performance schedule** (group PV → % of BV): 100→3%, 300→6%, 600→9%, 1,000→12%, 1,500→15%, 2,500→18%, 4,000→21%, 6,000→23%, 7,500→25%.
3. **Performance bonus** = `your% × personal BV` + for each frontline `max(0, your% − their%) × their group BV`.
4. **Max-bracket leg** = frontline whose group PV is in the top bracket (7,500 PV here).
5. **Leadership bonus (optional, default on)** ≈ 6% of those max-bracket frontline group BVs when you are also in the top bracket and have at least one such leg. Real leadership math includes adjustments this app omits.
6. **Ruby PV** = group PV minus max-bracket frontline group PV. Unofficial combined tiers: 10k/12.5k/15k ruby PV → 2%/4%/6% of ruby BV.
7. **Customer profit (optional)** = personal BV × margin (default 10%).
8. **Default BV per PV = 3.43** (a commonly cited North American ratio). Change it in Goals.
9. **Ranks** are generic planning labels (Starter → Diamond). Emerald-style = 3 max-bracket legs; Diamond-style = 6. Ruby-style also needs 15,000 ruby PV.
10. No annual bonuses, depth bonuses, compliance gates, or market-specific exceptions.

Change brackets, percents, and rank thresholds in `CompensationConfig` / `DefaultCompensation.kt`.

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
```
