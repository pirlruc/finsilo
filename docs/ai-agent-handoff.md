# AI Agent Handoff — finsilo

## Identity

| Field | Value |
| --- | --- |
| **App** | FinSilo |
| **Package** | `com.pirlruc.finsilo` |
| **Type** | Native Android (Kotlin, Compose), offline-first portfolio tracker |
| **Docs** | `docs/ai-agent-handoff.md`, `docs/improvements.md`, `docs/issues.yml`, `docs/limitations.md` |
| **Methodology** | [github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr) (Epic = decision record, no ADR markdown files) |

## Current slice

Product phases **1–6** are implemented. Guardrails (Phase 7): quality, Android lint/assemble/Robolectric, gitleaks, semgrep, PR dependency-review, and Dokka/KDoc are wired. [GATE-001-T3](issues.yml) Kover 95/95 is wired but not green. Untracked limits: [docs/limitations.md](limitations.md). Product decisions: [FS-DEC-001](issues.yml). Kotlin/Android gate map: [GATE-002](issues.yml).

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. FIFO ledger, valuator, history, TWR, YOC, signals, alerts, free-API parsers, `RecordLedgerEntryUseCase`, `SaveTargetAllocationUseCase`, `RebuildNavHistoryUseCase`. `Asset.locallyValued` covers CT/deposit and unlisted PPR. No Android APIs. |
| app | `app/` | Compose dashboard, ledger form, target settings, Vico charts, encrypted Room v4 (schema exported, including `nav_history`), OkHttp GET-only feed, WorkManager 23:00 sync + notifications, sample seeder. Ledger writes go through `saveLedgerEntry`. |

## How to run checks

```bash
bash scripts/ci-local.sh
./gradlew :domain:koverVerify   # expected red until LIM-COV
bash scripts/issues-sync.sh --validate-only
```

CI: `.github/workflows/quality.yml`, `domain-tests.yml` (tests + Kover verify), `android.yml`, `docs.yml`, `security.yml`. Actions are SHA-pinned. Numeric gates read `config/kotlin.profile.thresholds.yml` (consumer copy of the analog pin; LIM-SUB).

Toolchain notes that already bit this repo: AGP **8.10.0**, Kotlin **2.3.0**, Room **2.8.1 via kapt** (KSP 2.3 failed), compileSdk **36**, minSdk **26**. Do not set `jvmToolchain(17)` on this image (JDK 21 only); target 17 via `compilerOptions`. Vico 3.2.2 pie API is `pieSeries { series(...) }`. ktlint uses `android_studio` via `.editorconfig`. detekt `CyclomaticComplexMethod` max is 10 (`threshold: 11`).

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

GitHub publish is **blocked in this VM**: `CURSOR_REPO_READ_TOKEN` is a fine-grained PAT with `issues=read` (`X-Accepted-GitHub-Permissions: issues=read; pull_requests=read`). `setup-issue-scaffold.sh` and `issues-sync.py` therefore 403 on label/milestone/issue create. After the owner grants **Issues: Read and write** (and Contents remains as today), run:

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml
```

Then `--update` if rewriting bodies. Do not hand-create issues the manifest owns.

## What is still open after phases 1–6

Product leftovers (do not block calling 1–6 “shipped” except as noted):

- [FS-008](issues.yml) — kotlinx.serialization when a **third** JSON feed lands. Regex stays while the set is Frankfurter + AV + CoinGecko JSON plus Stooq CSV.

Phase 7 still open:

- [GATE-001-T3](issues.yml) — Kover 95/95. Task and CI job exist; **line is green, branch is ~73%**. See [limitations.md](limitations.md) LIM-COV.

Untracked limits (Kover 95/95, private analog clone, Semgrep registry, Kotlin MI metric, SBOM/signing, pre-commit gitleaks, emulator/SQLCipher, release minify, AV quota, EU suffixes, incremental NAV, GitHub Issues write): [limitations.md](limitations.md).

Do not record a fake lowered-gate deviation.

## Market feed (GET only)

| Asset | Source | Key |
| --- | --- | --- |
| FX EUR/USD | Frankfurter latest + `from..to` history, then Alpha Vantage `CURRENCY_EXCHANGE_RATE` | AV optional |
| Crypto | CoinGecko `market_chart` | none |
| EU listings (`.DE`, …) | Stooq daily CSV, then AV | AV optional |
| Commodities | Stooq `xauusd` for gold; AV `WTI`/`BRENT`/… or XAU FX (spot-only does not replace a stored series) | AV for non-XAU |
| US stocks / ratings | AV `TIME_SERIES_DAILY` (full) + `OVERVIEW` (skipped if rating is newer than 7 days) | AV |
| Unlisted PPR | Local NAV (buy + interest); skipped on sync | — |
| Listed PPR | `quoteSymbol` / exchange suffix; routed like an ETF | — |

The OkHttp client refuses non-GET. SMA is computed locally in `SyncMarketDataUseCase`. Quotes use `Asset.feedSymbol`. Execution FX on a USD trade does **not** overwrite `currency_history` when that date already has a row ([FS-014](issues.yml)). Empty FX history omits USD holdings from NAV and surfaces a dashboard warning ([FS-005](issues.yml)).

## Security

- SQLCipher passphrase and optional Alpha Vantage key in EncryptedSharedPreferences / Android Keystore.
- `android:allowBackup="false"` and backup exclusion rules.
- INTERNET permission for GET-only sync; cleartext disabled.
- `POST_NOTIFICATIONS` for Phase 5 alerts; skipped at runtime if denied (API 33+).

## Sample data

`SamplePortfolioFactory` is deterministic synthetic data (not market data). Includes AAPL (USD), VWCE.DE, BTC, unlisted PPR (ISIN on the asset row, interest stays in NAV), CT, deposit, XAU commodity, and three AAPL dividends for YOC. Loaded only from the empty-state button. AAPL’s last sample bar is forced through a golden cross for demo only ([FS-011](issues.yml)). Unlisted PPR has no invented daily quotes. Live sync and notifications use stored SMAs only.

*Last updated: 2026-08-17*
