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
| app | `app/` | Compose dashboard (split screens, holdings list, dual-currency quotes), ledger form (templates + manual close), PIN/biometric lock with recovery code, settings (targets, tax CSV/PDF, encrypted backup), watchlist, NAV home-screen widget, **broker CSV import** (T212 / DEGIRO / Revolut), Vico charts, encrypted Room v6 (schema exported, including thresholds, templates, watchlist, `nav_history`, `ledger_sequence`), OkHttp GET-only feed, WorkManager 23:00 one-shot reschedule + notifications, sample seeder. Ledger writes go through `saveLedgerEntry`. |

## How to run checks

```bash
bash scripts/ci-local.sh
./gradlew :domain:koverVerify
bash scripts/issues-sync.sh --validate-only
```

CI: `.github/workflows/quality.yml`, `domain-tests.yml` (tests + Kover verify), `android.yml`, `docs.yml`, `security.yml`. Actions are SHA-pinned. Numeric gates read analog `docs/guardrails/kotlin/profile.thresholds.yml` after `scripts/ci-init-guardrails.sh` (`GUARDRAILS_READ_TOKEN`); otherwise the consumer copy `config/kotlin.profile.thresholds.yml`. Do not clone `.github/scaffold` in CI.

Toolchain notes that already bit this repo: AGP **9.3.1** (built-in Kotlin — do not apply `org.jetbrains.kotlin.android`), Kotlin **2.4.10**, Room **2.8.4 via KSP 2.3.11**, Gradle **9.7.0**, compileSdk **37**, targetSdk **36**, minSdk **26**. Do not set `jvmToolchain(17)` on this image (JDK 21 only); target 17 via `compilerOptions`. OkHttp **5.4.0** (`Response.body` is non-null; do not implement `Interceptor.Chain` in tests). JUnit 6 needs `junit-platform-launcher` on `testRuntimeOnly`. `security-crypto` 1.1.0 stays on the classpath only for a one-shot EncryptedSharedPreferences migrator ([FS-028](issues.yml)); production prefs use Keystore AES-256-GCM (`com.pirlruc.finsilo.prefs_aes256_gcm`), not plaintext SharedPreferences. Vico 3.2.3 pie API is `pieSeries { series(...) }`. ktlint uses `android_studio` via `.editorconfig`. detekt `CyclomaticComplexMethod` max is 10 (`threshold: 11`).

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
- Open value backlog: [FS-008](issues.yml) (typed JSON parsers, deferred), [FS-026](issues.yml) Trading 212 official API. Closed this pass: backup [FS-017](issues.yml) (v6 extras, confirm restore, new-phone wrapping key), FIFO CSV/PDF [FS-018](issues.yml), NAV widget [FS-019](issues.yml), threshold alerts [FS-020](issues.yml), dual-currency holdings [FS-021](issues.yml), manual closes [FS-022](issues.yml), templates [FS-023](issues.yml), watchlist [FS-024](issues.yml), PIN-wrapped SQLCipher [FS-027-T2](issues.yml), Keystore AES-GCM prefs [FS-028](issues.yml). CSV import [FS-025](issues.yml) was already done. Do not reopen [FS-DEC-001](issues.yml). Drop `security-crypto` only after the ESP migrator has soaked.

Phase 7 quality/coverage/security gates are [GATE-001](issues.yml) (done, including Kover 95/95). Analog clone and detekt `@Composable` ignore leftovers are [GATE-003](issues.yml) (done).

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

The OkHttp client refuses non-GET and non-allowlisted HTTPS hosts. SMA is computed locally in `SyncMarketDataUseCase`. Quotes use `Asset.feedSymbol`. USD feeds (CoinGecko, AV US/commodities, Stooq XAU) stay USD and convert with stored EUR-per-USD even when the instrument is booked in EUR (`QuoteCurrency`). Execution FX on a USD trade does **not** overwrite `currency_history` when that date already has a row ([FS-014](issues.yml)). Empty FX history omits USD-quoted holdings from NAV and surfaces a dashboard warning ([FS-005](issues.yml)).

## Security

- SQLCipher wraps, optional Alpha Vantage key, PIN hashes, and widget NAV in Keystore AES-256-GCM SharedPreferences (`SecurePreferences`). Deprecated EncryptedSharedPreferences files migrate once into `{name}_ks`.
- First-launch PIN (4–8 digits), optional biometrics, and a one-time recovery code that resets the PIN. Recovery cannot reconstruct the PIN. PIN/recovery hashes use PBKDF2-HMAC-SHA256 at 210k iterations (off the main thread). Five failed PIN/recovery attempts start a 30s lockout that doubles, cap 15 minutes. Settings changes to biometrics or the recovery code require the current PIN. The session re-locks on `ON_STOP` (skipped while the biometric prompt is showing).
- SQLCipher passphrase parsing uses the same hex decoder as the lock (corrupt prefs fail closed; they do not throw `NumberFormatException`).
- Room v2→v4 additive schema changes are `AutoMigration` (no `execSQL` in `src/main`). Scanner scope filters are listed in [`docs/scanner-exceptions.md`](scanner-exceptions.md); there are no finding-level ignores.
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

*Last updated: 2026-08-19 (FS-028 Keystore AES-GCM prefs; PR #7 AGP 9 merged)*
