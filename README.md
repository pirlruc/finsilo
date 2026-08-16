# FinSilo

Privacy-first, offline-first Android tracker for multi-asset portfolios (stocks, ETFs, crypto, PPR, deposits, and Portuguese Certificados de Tesouro). Values, gains, and charts stay on-device.

This repository currently delivers **Phase 6 (dashboard)** plus the **encrypted local foundation** the charts require. Live API sync (Phase 3), full transaction-entry UI (Phase 2), and notification workers (Phase 5) are not in this tree.

## What works now

- Encrypted Room/SQLCipher ledger (assets, transactions, daily market history, FX, target allocation).
- On-device EUR valuation, allocation by asset class, and reconstructed NAV history.
- Jetpack Compose dashboard:
  - Donut chart of current allocation (Vico).
  - Line chart of portfolio NAV in EUR with 1M / 3M / YTD / All ranges (Vico).
  - Ratings and SMA 50/200 context for held marketable assets, including Golden/Death cross using the RFC consecutive-day rule.
- Empty state with an optional **synthetic** sample portfolio so the charts can be reviewed without network access.

## Design decisions (not silent RFC copies)

The RFC asked not to follow the spec blindly. These are the corrections already applied. Please say if any should be reverted.

| Topic | RFC | What we did | Why |
| --- | --- | --- | --- |
| `DailyMarketData` | “Overwritten or appended” | **Append**, composite PK `(asset_id, date)` | Line charts and SMA crosses need yesterday. Overwrite would destroy history. |
| Target weights | Used in drift alerts, **no table** | `target_allocation` table | Drift and the pie legend cannot exist without stored targets. |
| FX convention | Example `1.10 USD/EUR` and `native / rate` | Stored as **USD per 1 EUR**; EUR = USD / rate | Matches the formula. The example wording is easy to invert. |
| Cash | `DEPOSIT_CASH` exists; allocation pie has no cash slice | Uninvested EUR is a **Cash** slice | Buys without leftover cash would hide a real bucket; TWR later needs the same cashflow. |
| Money storage | “Decimal” | `BigDecimal` as **strings** in Room | IEEE floats are not acceptable for EUR cost basis. |
| SQLCipher | Required | `sqlcipher-android` + Keystore-backed passphrase | `android-database-sqlcipher` is deprecated. |
| Phase 5 vs 6 | Notifications only for ratings/SMA | Dashboard **also** shows ratings/SMA | A cross or rating change that only lives in a notification is easy to miss; the math is shared. Notifications can reuse `GetMarketSignalsUseCase`. |
| Yahoo unofficial JSON | Specified for prices/ratings | **Not implemented** | ToS/breakage risk. Phase 3 should pick official feeds (or user-imported CSV) before baking in unofficial endpoints. |
| Guardrails submodules | Pin private `guardrails` / `github-scaffold` | Documented gap | Those repos returned 404 with this token. Templates were not invented as a fake submodule. |

## Architecture

Clean architecture, two Gradle modules:

- `:domain` — pure Kotlin (JVM). Valuation, allocation, history, signals. Covered by JUnit 5.
- `:app` — Compose UI, Room, SQLCipher, Vico, ViewModel.

No Hilt. Constructor injection from `AppContainer` keeps the first slice small and testable.

## Build

Requirements: JDK 17+, Android SDK 35.

```bash
./gradlew :domain:test
./gradlew :app:assembleDebug
```

`local.properties` is gitignored. Point `sdk.dir` or `ANDROID_HOME` at your SDK.

There is **no INTERNET permission** yet. That is deliberate until GET-only sync exists.

## Methodology

[github-issue-adr](https://github.com/pirlruc/methodologies) (Epic = decision record, no ADR markdown files) and [guardrails](https://github.com/pirlruc/guardrails) were not cloneable from this environment. Living docs follow the public [heimdallcv](https://github.com/pirlruc/heimdallcv) analog:

- [`docs/ai-agent-handoff.md`](docs/ai-agent-handoff.md)
- [`docs/improvements.md`](docs/improvements.md)
