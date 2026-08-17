# AI Agent Handoff — finsilo

## Identity

| Field | Value |
| --- | --- |
| **App** | FinSilo |
| **Package** | `com.pirlruc.finsilo` |
| **Type** | Native Android (Kotlin, Compose), offline-first portfolio tracker |
| **Docs** | `docs/ai-agent-handoff.md`, `docs/improvements.md` |
| **Methodology** | [github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr) (Epic = decision record, no ADR markdown files) |

## Current slice

Phase 2 data entry (O1–O4, O6 fields) + Phase 6 dashboard + encrypted Room/SQLCipher + GET-only market sync + TWR/YOC + analog pins (O14).

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. FIFO ledger, valuator, history, TWR, YOC, signals, free-API parsers, `RecordLedgerEntryUseCase`, `SaveTargetAllocationUseCase`. No Android APIs. |
| app | `app/` | Compose dashboard, ledger form, target settings, Vico charts, encrypted Room v3, OkHttp GET-only feed, WorkManager 23:00 sync, sample seeder. |

## How to run checks

```bash
./gradlew :domain:test
./gradlew :app:assembleDebug
```

Toolchain notes that already bit this repo: AGP **8.10.0**, Kotlin **2.3.0**, Room **2.8.1 via kapt** (KSP 2.3 failed), compileSdk **36**, minSdk **26**. Do not set `jvmToolchain(17)` on this image (JDK 21 only); target 17 via `compilerOptions`. Vico 3.2.2 pie API is `pieSeries { series(...) }`.

## Analog pins (O14)

| Companion | How it is pinned |
| --- | --- |
| `pirlruc/guardrails` | Git submodule SHA at `docs/guardrails/` (tag **1.3.0**) |
| `pirlruc/github-scaffold` | Git submodule SHA at `.github/scaffold/` (tag **1.2.0**); templates synced into `.github/` and `.cursor/rules/` |
| `pirlruc/methodologies` | Cited by tag in docs (`tree/1.2.0/github-issue-adr`). Not a submodule. Folders: `docs/guardrails/`, `.github/scaffold/`. |

Private clones need `CURSOR_REPO_READ_TOKEN` (GitHub PAT) at VM start. `git submodule update --init` uses the public HTTPS URLs in `.gitmodules`.

Heimdall (`pirlruc/heimdall`) is an Android **SDK** stack (app → kit → core). FinSilo is an app: keep two Gradle modules (`:domain`, `:app`) and constructor injection. Do not copy Heimdall's kit/native layout.

## Intentionally not in this tree

- Phase 5 NotificationManager worker (signal detection **does** exist and is shown on the dashboard). Daily quote sync **is** scheduled (O13).
- Phase 3 hardening O5 / O7 / O9 (listed PPR quote routing beyond storing ISIN/`quoteSymbol`).
- Full Room schema export / ending remaining destructive fallback from v1 (O12 remainder).

Open issues with phase and recommended fix: [`docs/improvements.md`](improvements.md).

## Market feed (GET only)

| Asset | Source | Key |
| --- | --- | --- |
| FX EUR/USD | Frankfurter latest + `from..to` history, then Alpha Vantage `CURRENCY_EXCHANGE_RATE` | AV optional |
| Crypto | CoinGecko `market_chart` | none |
| EU listings (`.DE`, …) | Stooq daily CSV, then AV | AV optional |
| Commodities | Stooq `xauusd` for gold; AV `WTI`/`BRENT`/… or XAU FX | AV for non-XAU |
| US stocks / ratings | AV `TIME_SERIES_DAILY` (full) + `OVERVIEW` | AV |
| PPR | Skipped unless `quoteSymbol` is set | — |

The OkHttp client refuses non-GET. SMA is computed locally in `SyncMarketDataUseCase`. Quotes use `Asset.feedSymbol`.

## Security

- SQLCipher passphrase and optional Alpha Vantage key in EncryptedSharedPreferences / Android Keystore.
- `android:allowBackup="false"` and backup exclusion rules.
- INTERNET permission for GET-only sync; cleartext disabled.

## Sample data

`SamplePortfolioFactory` is deterministic synthetic data (not market data). Includes AAPL (USD), VWCE.DE, BTC, PPR (ISIN on the asset row), CT, deposit, XAU commodity, and three AAPL dividends for YOC. Loaded only from the empty-state button. AAPL’s last sample bar is forced through a golden cross for demo only (O11).

*Last updated: 2026-08-17*
