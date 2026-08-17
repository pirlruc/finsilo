# AI Agent Handoff — finsilo

## Identity

| Field | Value |
| --- | --- |
| **App** | FinSilo |
| **Package** | `com.pirlruc.finsilo` |
| **Type** | Native Android (Kotlin, Compose), offline-first portfolio tracker |
| **Docs** | `docs/ai-agent-handoff.md`, `docs/improvements.md` |

## Current slice

Phase 6 dashboard + encrypted Room/SQLCipher foundation + GET-only market sync + TWR/YOC.

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. FIFO ledger, valuator, history, TWR, YOC, signals, free-API parsers. No Android APIs. |
| app | `app/` | Compose dashboard, Vico charts, encrypted Room, OkHttp GET-only feed, WorkManager 23:00 sync, sample seeder. |

## How to run checks

```bash
./gradlew :domain:test
./gradlew :app:assembleDebug
```

Toolchain notes that already bit this repo: AGP **8.10.0**, Kotlin **2.3.0**, Room **2.8.1 via kapt** (KSP 2.3 failed), compileSdk **36**, minSdk **26**. Do not set `jvmToolchain(17)` on this image (JDK 21 only); target 17 via `compilerOptions`. Vico 3.2.2 pie API is `pieSeries { series(...) }`.

## Intentionally not in this tree

- Phase 2 Compose forms for Buy/Sell/Deposit/Withdrawal.
- Phase 5 NotificationManager worker (signal detection **does** exist and is shown on the dashboard). Daily quote sync **is** scheduled.
- Private `guardrails` / `methodologies` / `github-scaffold` submodules until `CURSOR_REPO_READ_TOKEN` is injected.

## Market feed (GET only)

| Asset | Source | Key |
| --- | --- | --- |
| FX EUR/USD | Frankfurter, then Alpha Vantage `CURRENCY_EXCHANGE_RATE` | AV optional |
| Crypto | CoinGecko `market_chart` | none |
| EU listings (`.DE`, …) | Stooq daily CSV, then AV | AV optional |
| Commodities | Stooq `xauusd` for gold; AV `WTI`/`BRENT`/… or XAU FX | AV for non-XAU |
| US stocks / ratings | AV `TIME_SERIES_DAILY` (full) + `OVERVIEW` | AV |

The OkHttp client refuses non-GET. SMA is computed locally in `SyncMarketDataUseCase`.

## Security

- SQLCipher passphrase and optional Alpha Vantage key in EncryptedSharedPreferences / Android Keystore.
- `android:allowBackup="false"` and backup exclusion rules.
- INTERNET permission for GET-only sync; cleartext disabled.

## Sample data

`SamplePortfolioFactory` is deterministic synthetic data (not market data). Includes AAPL (USD), VWCE.DE, BTC, PPR, CT, deposit, XAU commodity, and three AAPL dividends for YOC. Loaded only from the empty-state button.

*Last updated: 2026-08-17*
