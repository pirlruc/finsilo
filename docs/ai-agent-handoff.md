# AI Agent Handoff — finsilo

## Identity

| Field | Value |
| --- | --- |
| **App** | FinSilo |
| **Package** | `com.pirlruc.finsilo` |
| **Type** | Native Android (Kotlin, Compose), offline-first portfolio tracker |
| **Docs** | `docs/ai-agent-handoff.md`, `docs/issues.yml`, `docs/limitations.md` |
| **Methodology** | [github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr) (Epic = decision record, no ADR markdown files) |

## Current slice

Product phases **1–6** are implemented. Guardrails (Phase 7): quality (including domain SEI maintainability), Android lint/assembleDebug/assembleRelease/Robolectric, gitleaks, pre-commit, semgrep, CodeQL, OSV Scanner, MobSF mobsfscan, PR dependency-review, Dokka/KDoc, CycloneDX SBOM, and Kover 95/95 ([GATE-001](issues.yml) done). Untracked limits: [docs/limitations.md](limitations.md).

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. FIFO ledger, valuator, history, TWR, YOC, signals, alerts, free-API parsers, broker CSV import, `RecordLedgerEntryUseCase`, `SaveTargetAllocationUseCase`, `RebuildNavHistoryUseCase`, `QuoteCurrency` (USD feeds × FX → EUR), `AppLockCrypto`. `Asset.locallyValued` covers CT/deposit and unlisted PPR. No Android APIs. |
| app | `app/` | Compose dashboard (split screens, holdings list, dual-currency quotes, **per-holding rating alert prefs + instrument edit**), ledger form (templates + manual close; **buys auto-fund a same-day cash deposit when cash is short**; **mark-to-market new buys probe the feed before persist**), PIN/biometric lock with recovery code (**overlay relock, process lifecycle**; **biometric Keystore wrap of the SQLCipher key for cold start**, PIN fallback), settings (targets, tax CSV/PDF, encrypted backup), watchlist (**quote probe on add**, rating prefs), NAV home-screen widget, **broker CSV import** (T212 / DEGIRO / Revolut) with **quote-symbol review** (lots always persist), Vico charts (**full pie for a single 100% slice**), encrypted Room **v7** (schema exported, including thresholds, templates, watchlist, `nav_history`, `ledger_sequence`, **`rating_alert`**), OkHttp GET-only feed, WorkManager 23:00 one-shot reschedule + notifications, sample seeder. Single-row ledger writes go through `saveLedgerEntry`; funded buys persist via `persistImport` so deposit+buy stay one Room transaction without extra repository methods (KT-CPLX-002). |

## How to run checks

```bash
bash scripts/ci-local.sh
./gradlew :domain:koverVerify
bash scripts/issues-sync.sh --validate-only
```

CI: `.github/workflows/quality.yml`, `domain-tests.yml` (tests + Kover verify), `android.yml`, `docs.yml`, `security.yml`. Actions are SHA-pinned. Numeric gates read analog `docs/guardrails/kotlin/profile.thresholds.yml` after `scripts/ci-init-guardrails.sh` (`GUARDRAILS_READ_TOKEN`); otherwise the consumer copy `config/kotlin.profile.thresholds.yml`. Do not clone `.github/scaffold` in CI.

Toolchain notes that already bit this repo: AGP **9.3.1** (built-in Kotlin — do not apply `org.jetbrains.kotlin.android`), Kotlin **2.4.10**, Room **2.8.4 via KSP 2.3.11**, Gradle **9.7.0**, compileSdk **37**, targetSdk **36**, minSdk **26**. Do not set `jvmToolchain(17)` on this image (JDK 21 only); target 17 via `compilerOptions`. OkHttp **5.4.0** (`Response.body` is non-null; do not implement `Interceptor.Chain` in tests). JUnit 6 needs `junit-platform-launcher` on `testRuntimeOnly`. Prefs use Keystore AES-256-GCM (`com.pirlruc.finsilo.prefs_aes256_gcm`); do not add `security-crypto` / EncryptedSharedPreferences. Robolectric’s JVM has no `AndroidKeyStore` provider — unit tests inject a software AES-GCM AEAD; production `open()` constructs the Keystore AEAD eagerly so the widget still fail-closes. Vico 3.2.3 pie API is `pieSeries { series(...) }`. ktlint uses `android_studio` via `.editorconfig`. detekt is **2.0.0-alpha.6** (`dev.detekt`; do not apply `io.gitlab.arturbosch.detekt` — 1.23.8 calls deprecated `ReportingExtension.file`, and there is no 1.23.9). `CyclomaticComplexMethod` max is 10 (`allowedComplexity: 10`; 2.x is inclusive). Fail on Warning+ (`failOnSeverity`), not the 2.x Error default.

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
- Open value backlog: [FS-008](issues.yml) (typed JSON parsers, deferred), [FS-026](issues.yml) Trading 212 official API. Active: [FS-030](issues.yml) quote probe, biometric DB wrap, rating prefs, backup export. Closed this pass leftovers: backup [FS-017](issues.yml) (v6 extras, confirm restore, new-phone wrapping key), FIFO CSV/PDF [FS-018](issues.yml), NAV widget [FS-019](issues.yml), threshold alerts [FS-020](issues.yml), dual-currency holdings [FS-021](issues.yml), manual closes [FS-022](issues.yml), templates [FS-023](issues.yml), watchlist [FS-024](issues.yml), PIN-wrapped SQLCipher [FS-027-T2](issues.yml), Keystore AES-GCM prefs [FS-028](issues.yml). CSV import [FS-025](issues.yml) was already done. Do not reopen [FS-DEC-001](issues.yml). FS-027's "biometric does not unwrap on a cold process" is superseded by FS-030.

Phase 7 quality/coverage/security gates are [GATE-001](issues.yml) (done, including Kover 95/95). Analog clone and detekt `@Composable` ignore leftovers are [GATE-003](issues.yml) (done). Gradle 9 `ReportingExtension.file` deprecation is [GATE-004](issues.yml) (done; detekt 2.x).

Untracked limits (Semgrep registry, signing/release, emulator/SQLCipher, AV quota, GitHub Issues write): [limitations.md](limitations.md).

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

Sync skips names whose last stored bar date is already `asOf` and requests the rest oldest-first (never-quoted first) so one Alpha Vantage key is not wasted on fresh symbols. Watchlist `OVERVIEW` runs when the effective rating-alert set is non-empty (defaults are Buy / Strong Buy).

The OkHttp client refuses non-GET and non-allowlisted HTTPS hosts. SMA is computed locally in `SyncMarketDataUseCase`. Quotes use `Asset.feedSymbol`. USD feeds (CoinGecko, AV US/commodities, Stooq XAU) stay USD and convert with stored EUR-per-USD even when the instrument is booked in EUR (`QuoteCurrency`). Execution FX on a USD trade does **not** overwrite `currency_history` when that date already has a row ([FS-014](issues.yml)). Empty FX history omits USD-quoted holdings from NAV and surfaces a dashboard warning ([FS-005](issues.yml)).

## Security

- SQLCipher wraps, optional Alpha Vantage key, PIN hashes, and widget NAV in Keystore AES-256-GCM SharedPreferences (`SecurePreferences`).
- First-launch PIN (4–8 digits), optional biometrics, and a one-time recovery code that resets the PIN. Recovery cannot reconstruct the PIN. PIN/recovery hashes use PBKDF2-HMAC-SHA256 at 210k iterations (off the main thread). Five failed PIN/recovery attempts start a 30s lockout that doubles, cap 15 minutes. Settings changes to biometrics or the recovery code require the current PIN. The session re-locks when the **process** goes to the background (`ProcessLifecycleOwner` `ON_STOP`; skipped while the biometric prompt or an external picker is showing). Relock overlays the PIN screen so in-progress ledger fields are kept. An empty Unlock tap does not count as a failed attempt. Recovery code screens have a **Copy** button; the clipboard is cleared after 60s if it still holds the code (`FLAG_SECURE` stays on the activity except while a document picker is open).
- SQLCipher passphrase parsing uses the same hex decoder as the lock (corrupt prefs fail closed; they do not throw `NumberFormatException`). PIN and recovery wrap the 32-byte key; a third wrap is Keystore AES-GCM with `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`. Cold start can unwrap with biometrics; **Use PIN** or failure falls back to PIN. Enrollment change shows “Biometrics changed. Enter PIN.” then reseals. Backup files are FSILO-LEDGER-3 (rating alert rows); decode still accepts v1/v2.
- Room v2→v7 additive schema changes are `AutoMigration` (no `execSQL` in `src/main`). Scanner scope filters are listed in [`docs/scanner-exceptions.md`](scanner-exceptions.md); there are no finding-level ignores.
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

*Last updated: 2026-08-21 (FS-030: quote probe, biometric DB wrap, rating prefs, backup export, Room v7)*
