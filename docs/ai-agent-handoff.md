# AI Agent Handoff — finsilo

## Identity

| Field | Value |
| --- | --- |
| **App** | FinSilo |
| **Package** | `com.pirlruc.finsilo` |
| **Type** | Native Android (Kotlin, Compose), offline-first portfolio tracker |
| **Docs** | `docs/ai-agent-handoff.md`, `docs/improvements.md`, `docs/issues.yml` |
| **Methodology** | [github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr) (Epic = decision record, no ADR markdown files) |

## Current slice

Phase 2 data entry + bug pass (unlisted PPR NAV, FX seed vs MTM, atomic ledger write, decimal parse) + analog pins + authored `docs/issues.yml`.

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. FIFO ledger, valuator, history, TWR, YOC, signals, free-API parsers, `RecordLedgerEntryUseCase`, `SaveTargetAllocationUseCase`. `Asset.locallyValued` covers CT/deposit and unlisted PPR. No Android APIs. |
| app | `app/` | Compose dashboard, ledger form, target settings, Vico charts, encrypted Room v3, OkHttp GET-only feed, WorkManager 23:00 sync, sample seeder. Ledger writes go through `saveLedgerEntry`. |

## How to run checks

```bash
./gradlew :domain:test
./gradlew :app:assembleDebug
bash scripts/issues-sync.sh --validate-only
```

CI: `.github/workflows/domain-tests.yml` (`:domain:test`, SHA-pinned actions, [GATE-001-T1](issues.yml)).

Toolchain notes that already bit this repo: AGP **8.10.0**, Kotlin **2.3.0**, Room **2.8.1 via kapt** (KSP 2.3 failed), compileSdk **36**, minSdk **26**. Do not set `jvmToolchain(17)` on this image (JDK 21 only); target 17 via `compilerOptions`. Vico 3.2.2 pie API is `pieSeries { series(...) }`.

## Analog pins (TOOL-001 / O14)

| Companion | How it is pinned |
| --- | --- |
| `pirlruc/guardrails` | Git submodule SHA at `docs/guardrails/` (tag **1.3.0**) |
| `pirlruc/github-scaffold` | Git submodule SHA at `.github/scaffold/` (tag **1.2.0**); templates synced into `.github/` and `.cursor/rules/` |
| `pirlruc/methodologies` | Cited by tag in docs (`tree/1.2.0/github-issue-adr`). Not a submodule. Folders: `docs/guardrails/`, `.github/scaffold/`. |

Private clones need `CURSOR_REPO_READ_TOKEN` (GitHub PAT) at VM start. `git submodule update --init` uses the public HTTPS URLs in `.gitmodules`.

Heimdall (`pirlruc/heimdall`) is an Android **SDK** stack (app → kit → core). FinSilo is an app: keep two Gradle modules (`:domain`, `:app`) and constructor injection. Do not copy Heimdall's kit/native layout.

## github-issue-adr

Authored backlog: [`docs/issues.yml`](issues.yml). Targets: [`docs/issues-sync-targets.yml`](issues-sync-targets.yml). Deviations (none): [`docs/guardrail-deviations.yml`](guardrail-deviations.yml). Do not invent `approved_by`.

Root wrappers call the submodule (do not vendor cppdevops CI):

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
bash scripts/sync-templates.sh
```

Issue content lives only in `docs/issues.yml`. Changing an `id` orphans the GitHub issue.

## Intentionally not in this tree

- Phase 5 NotificationManager worker (signal detection **does** exist and is shown on the dashboard). Daily quote sync **is** scheduled ([FS-013](issues.yml)).
- Phase 3 hardening [FS-005](issues.yml) / [FS-007](issues.yml) / [FS-009](issues.yml) (listed PPR quote routing is [FS-006-T3](issues.yml)).
- Full Room schema export / ending remaining destructive fallback from v1 ([FS-012-T2](issues.yml)).
- ktlint/detekt/Kover/gitleaks/semgrep ([GATE-001-T2](issues.yml)…T4). Do not record a fake lowered-gate deviation.

Open issues with phase and recommended fix: [`docs/improvements.md`](improvements.md) (map) and [`docs/issues.yml`](issues.yml) (source of truth).

## Market feed (GET only)

| Asset | Source | Key |
| --- | --- | --- |
| FX EUR/USD | Frankfurter latest + `from..to` history, then Alpha Vantage `CURRENCY_EXCHANGE_RATE` | AV optional |
| Crypto | CoinGecko `market_chart` | none |
| EU listings (`.DE`, …) | Stooq daily CSV, then AV | AV optional |
| Commodities | Stooq `xauusd` for gold; AV `WTI`/`BRENT`/… or XAU FX | AV for non-XAU |
| US stocks / ratings | AV `TIME_SERIES_DAILY` (full) + `OVERVIEW` | AV |
| Unlisted PPR | Local NAV (buy + interest); skipped on sync | — |
| Listed PPR | `quoteSymbol` / exchange suffix; route like ETF ([FS-006-T3](issues.yml) still open) | — |

The OkHttp client refuses non-GET. SMA is computed locally in `SyncMarketDataUseCase`. Quotes use `Asset.feedSymbol`. Execution FX on a USD trade does **not** overwrite `currency_history` when that date already has a row ([FS-014](issues.yml)).

## Security

- SQLCipher passphrase and optional Alpha Vantage key in EncryptedSharedPreferences / Android Keystore.
- `android:allowBackup="false"` and backup exclusion rules.
- INTERNET permission for GET-only sync; cleartext disabled.

## Sample data

`SamplePortfolioFactory` is deterministic synthetic data (not market data). Includes AAPL (USD), VWCE.DE, BTC, unlisted PPR (ISIN on the asset row, interest stays in NAV), CT, deposit, XAU commodity, and three AAPL dividends for YOC. Loaded only from the empty-state button. AAPL’s last sample bar is forced through a golden cross for demo only ([FS-011](issues.yml)). Unlisted PPR has no invented daily quotes.

*Last updated: 2026-08-17*
