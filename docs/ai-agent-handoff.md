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

Product phases **1–6** are implemented. Guardrails (Phase 7): quality (including domain SEI maintainability), Android lint/assembleDebug/assembleRelease/Robolectric, gitleaks, pre-commit, semgrep, CodeQL, OSV Scanner, MobSF mobsfscan, PR dependency-review, Dokka/KDoc, and CycloneDX SBOM are wired. [GATE-001-T3](issues.yml) Kover branch 95% is wired but not green. Untracked limits: [docs/limitations.md](limitations.md).

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. FIFO ledger, valuator, history, TWR, YOC, signals, alerts, free-API parsers, broker CSV import, `RecordLedgerEntryUseCase`, `SaveTargetAllocationUseCase`, `RebuildNavHistoryUseCase`, `QuoteCurrency` (USD feeds × FX → EUR), `AppLockCrypto`. `Asset.locallyValued` covers CT/deposit and unlisted PPR. No Android APIs. |
| app | `app/` | Compose dashboard (split screens), ledger form (buy/sell every type), PIN/biometric lock with recovery code, target settings, **broker CSV import** (T212 / DEGIRO / Revolut), Vico charts, encrypted Room v4 (schema exported, including `nav_history`), OkHttp GET-only feed, WorkManager 23:00 sync + notifications, sample seeder. Ledger writes go through `saveLedgerEntry`. |

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
- Open value backlog (not started): [FS-017](issues.yml) encrypted backup, [FS-018](issues.yml) PT FIFO report, [FS-019](issues.yml) NAV widget, [FS-020](issues.yml) threshold alerts, [FS-021](issues.yml) dual-currency display, [FS-022](issues.yml) manual quotes, [FS-023](issues.yml) templates, [FS-024](issues.yml) watchlist, [FS-026](issues.yml) Trading 212 official API (free key, not paid). CSV import is [FS-025](issues.yml) (done). Do not reopen [FS-DEC-001](issues.yml).

Phase 7 still open:

- [GATE-001-T3](issues.yml) — Kover 95/95. Task and CI job exist; **line is green, branch is ~89%**. See [limitations.md](limitations.md) LIM-COV.

Untracked limits (Kover branch 95, private analog clone, Semgrep registry, signing/release, emulator/SQLCipher, AV quota, GitHub Issues write): [limitations.md](limitations.md).

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

The OkHttp client refuses non-GET and non-allowlisted HTTPS hosts. SMA is computed locally in `SyncMarketDataUseCase`. Quotes use `Asset.feedSymbol`. USD feeds (CoinGecko, AV US/commodities, Stooq XAU) stay USD and convert with stored EUR-per-USD even when the instrument is booked in EUR (`QuoteCurrency`). Execution FX on a USD trade does **not** overwrite `currency_history` when that date already has a row ([FS-014](issues.yml)). Empty FX history omits USD-quoted holdings from NAV and surfaces a dashboard warning ([FS-005](issues.yml)).

## Security

- SQLCipher passphrase and optional Alpha Vantage key in EncryptedSharedPreferences / Android Keystore.
- First-launch PIN (4–8 digits), optional biometrics, and a one-time recovery code that resets the PIN. Recovery cannot reconstruct the PIN. PIN/recovery hashes use PBKDF2-HMAC-SHA256 at 210k iterations.
- `FLAG_SECURE` and `filterTouchesWhenObscured` on the main activity (no screenshots of the ledger/PIN; ignore overlay taps).
- `android:allowBackup="false"` and backup exclusion rules.
- INTERNET permission for GET-only sync; cleartext disabled; quote URLs built with `HttpUrl.Builder` and restricted to Frankfurter / Alpha Vantage / CoinGecko / Stooq.
- `POST_NOTIFICATIONS` for Phase 5 alerts; skipped at runtime if denied (API 33+).

## Security scanners vs tools already in place

None of CodeQL, OSV Scanner, or Mobile Security Framework were in the repo before GATE-001-T6. They **complement** the existing CI-012 jobs; they do not replace them.

| Tool | Role | Overlap |
| --- | --- | --- |
| **gitleaks** (already in `security.yml`) | Secrets in git history | Not replaced. CodeQL/MobSF may also flag hardcoded secrets; gitleaks is the secret-scan gate (KT-SEC-003). |
| **semgrep** `r/kotlin` + `r/generic.secrets` (already) | Pattern SAST, ERROR+ | Complementary to **CodeQL** (interprocedural dataflow) and **mobsfscan** (Android/MobSF rules). Kotlin pack default is semgrep-only; CodeQL is extra. |
| **dependency-review-action** (already, PRs only) | GitHub Advisory on the PR diff | Complementary to **OSV Scanner**, which scans the full tree / version catalog and the CycloneDX SBOM against OSV.dev on every push. |
| **syft** CycloneDX (already in `android.yml`) | SBOM inventory, not a vuln gate | OSV can consume that SBOM when it exists. |
| **Android lint / detekt** | API/quality | Not MobSF. lint may catch some manifest issues; mobsfscan is MASVS-oriented. |
| **CodeQL** (new) | Interprocedural SAST (`java-kotlin`, `security-extended`) | Not previously included. CI-only (`github/codeql-action`). |
| **OSV Scanner** (new) | Dependency vulns (OSV.dev) against the Gradle CycloneDX BOM (`cyclonedxBom`, runtime classpaths) | Not previously included. Local: `scripts/run-osv-scanner.sh`. Complements PR-only dependency-review. |
| **MobSF mobsfscan** (new) | Mobile Security Framework source SAST | Not previously included. Full MobSF Docker APK analysis is not in CI (no Docker in this flow); mobsfscan is the CI-practical MobSF gate. |

## Sample data

`SamplePortfolioFactory` is deterministic synthetic data (not market data). Includes AAPL (USD), VWCE.DE, BTC, unlisted PPR (ISIN on the asset row, interest stays in NAV), CT, deposit, XAU commodity, and three AAPL dividends for YOC. Loaded only from the empty-state button. AAPL’s last sample bar is forced through a golden cross for demo only ([FS-011](issues.yml)). Unlisted PPR has no invented daily quotes. Live sync and notifications use stored SMAs only.

*Last updated: 2026-08-18*
