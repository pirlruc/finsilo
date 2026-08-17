# FinSilo

Privacy-first, offline-first Android tracker for multi-asset portfolios (stocks, ETFs, crypto, PPR, deposits, Portuguese Certificados de Tesouro, and commodities). Values, gains, and charts stay on-device.

This repository currently delivers **Phase 6 (dashboard)** plus the **encrypted local foundation**, **GET-only market sync**, and **TWR / YOC** the charts require. Full transaction-entry UI (Phase 2) and notification workers (Phase 5) are not in this tree.

## What works now

- Encrypted Room/SQLCipher ledger (assets, transactions, daily market history, FX, target allocation).
- On-device EUR valuation (FIFO cost basis), allocation by asset class, reconstructed NAV history, TWR, and dual YOC.
- Jetpack Compose dashboard:
  - Donut chart of current allocation by investment type (Vico).
  - Line chart of portfolio NAV in EUR with 1M / 3M / YTD / All ranges (Vico).
  - TWR, yield on cost (TTM and last payment × frequency), ratings, and SMA 50/200.
- Optional **Sync** using free APIs. An Alpha Vantage key is stored on-device when you have one; Frankfurter (including FX history), Stooq, and CoinGecko work without a key.
- Empty state with a **synthetic** sample portfolio so the charts can be reviewed without network access.

## Design decisions (not silent RFC copies)

| Topic | Decision |
| --- | --- |
| FX | Stored and applied as **EUR per 1 USD**. USD → EUR is `native * eurPerUsd`. |
| Pie slices | One slice per investment type (Cash, CT, Deposit, Commodity, …). CT/deposit interest stays inside those instruments. |
| Cost basis | **FIFO lots** (oldest buy consumed first), aligned with Portuguese capital-gains reporting. |
| Quotes | Free GET-only feeds only. **No unofficial Yahoo JSON.** Alpha Vantage (optional key, ~25 calls/day), Frankfurter FX, Stooq EU/XAU, CoinGecko crypto. SMA 50/200 are computed locally so AV quota is not spent on SMA. |
| Commodities | Mark-to-market: current price versus FIFO buy cost, same as stocks. XAU via Stooq (`xauusd`) or Alpha Vantage `CURRENCY_EXCHANGE_RATE`; WTI/BRENT/… via Alpha Vantage commodity series. |
| TWR splits | Only **buys funded with external cash** (cost exceeds uninvested cash) and **withdrawals**. `DEPOSIT_CASH`, internal buys, dividends, and interest do not open a sub-period. |
| YOC | Both **TTM** (dividends in the last 365 days / remaining FIFO cost) and **last payment × inferred frequency** (1, 2, 4, or 12 from the TTM count). |
| minSdk | **26** (java.time + Keystore defaults). |
| Guardrails / methodologies / github-scaffold | Private. This environment requested `CURSOR_REPO_READ_TOKEN`; until it is injected those repos stay unpinned. Templates were not invented as a fake submodule. |

## Architecture

Clean architecture, two Gradle modules:

- `:domain` — pure Kotlin (JVM). Valuation, FIFO ledger, allocation, history, TWR, YOC, signals, feed parsers. Covered by JUnit 5.
- `:app` — Compose UI, Room, SQLCipher, Vico, WorkManager (23:00 daily sync), OkHttp GET-only client.

No Hilt. Constructor injection from `AppContainer` keeps the first slice small and testable.

## Build

Requirements: JDK 17+, Android SDK 35/36.

```bash
./gradlew :domain:test
./gradlew :app:assembleDebug
```

`local.properties` is gitignored. Point `sdk.dir` or `ANDROID_HOME` at your SDK.

The app requests **INTERNET** for GET-only quote sync. Cleartext is disabled.

## Methodology

[github-issue-adr](https://github.com/pirlruc/methodologies) (Epic = decision record, no ADR markdown files) and [guardrails](https://github.com/pirlruc/guardrails) were not cloneable from this environment without `CURSOR_REPO_READ_TOKEN`. Living docs follow the public [heimdallcv](https://github.com/pirlruc/heimdallcv) analog:

- [`docs/ai-agent-handoff.md`](docs/ai-agent-handoff.md)
- [`docs/improvements.md`](docs/improvements.md)
