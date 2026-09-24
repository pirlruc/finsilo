# AI Agent Handoff — finsilo

## Identity

| Field | Value |
| --- | --- |
| **App** | FinSilo |
| **Package** | `com.pirlruc.finsilo` |
| **Type** | Native Android (Kotlin, Compose), offline-first portfolio tracker |
| **Docs** | `docs/ai-agent-handoff.md`, `docs/issues.yml`, `docs/limitations.md` |
| **Methodology** | [github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.5.0/github-issue-adr) (Epic = decision record, no ADR markdown files) |

## Current slice

Product phases **1–6** are implemented. Guardrails (Phase 7): quality (including domain SEI maintainability), Android lint/assembleDebug/assembleRelease/Robolectric, gitleaks, pre-commit, semgrep, CodeQL, OSV Scanner, MobSF mobsfscan, PR dependency-review, Dokka/KDoc, CycloneDX SBOM, and Kover 95/95 ([GATE-001](issues.yml) done). Untracked limits: [docs/limitations.md](limitations.md).

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. FIFO ledger, valuator, history, TWR, YOC, signals, alerts, free-API parsers, broker CSV import, `RecordLedgerEntryUseCase`, `SaveTargetAllocationUseCase`, `RebuildNavHistoryUseCase`, `QuoteCurrency` (USD feeds × FX → EUR), `AppLockCrypto`. `Asset.locallyValued` covers CT/deposit and unlisted PPR. No Android APIs. |
| app | `app/` | Compose dashboard (split screens, holdings list, dual-currency quotes, **per-holding rating alert prefs + instrument edit**, **signed P&L colours**, **capital card with money-in vs cash interest vs gain/loss per broker**), ledger form (templates + manual close; **buys auto-fund a same-day cash deposit when cash is short**; **mark-to-market new buys probe the feed before persist**), PIN/biometric lock with recovery code (**overlay relock, process lifecycle**; **hybrid: overlay immediately, SQLCipher session evicted after 15 minutes in the background**; **biometric Keystore wrap of the SQLCipher key for cold start**, PIN fallback), settings (targets, tax CSV/PDF, encrypted backup), watchlist (**quote probe on add**, rating prefs), NAV home-screen widget, **broker CSV import** (T212 / DEGIRO / Revolut; **repeatable**, localized DEGIRO/Revolut headers, split cash columns) with **quote-symbol review** (lots always persist), Vico charts (**full pie for a single 100% slice**), encrypted Room **v9** (schema exported; **`broker_source`** on transactions; v8 unread nav columns still apply), OkHttp GET-only feed (**Stooq `stooq.pl/q/d/l/`** with allowlisted redirects), WorkManager 23:00 one-shot reschedule + notifications, sample seeder. Single-row ledger writes go through `saveLedgerEntry`; funded buys persist via `persistImport` so deposit+buy stay one Room transaction without extra repository methods (KT-CPLX-002). Dashboard loads quotes for the selected NAV chip; stored daily bars start at first purchase. |

## How to run checks

```bash
bash scripts/ci-local.sh
./gradlew :domain:koverVerify
bash scripts/issues-sync.sh --validate-only
bash scripts/check-analog-pins.sh
```

CI: `.github/workflows/quality.yml`, `domain-tests.yml` (tests + Kover verify), `android.yml`, `docs.yml`, `security.yml`. Actions are SHA-pinned. Numeric gates read analog `docs/guardrails/kotlin/profile.thresholds.yml` after `scripts/ci-init-guardrails.sh` (`GUARDRAILS_READ_TOKEN`); clone failure or an unset token uses the consumer copy `config/kotlin.profile.thresholds.yml`. Android jobs pin `android-actions/setup-android` **v4.0.4** (`be39fa83…`; v4.0.1 fails on runner cmdline-tools 20). Do not clone `.github/scaffold` in CI.

Toolchain notes that already bit this repo: AGP **9.3.2** (built-in Kotlin — do not apply `org.jetbrains.kotlin.android`), Kotlin **2.4.10**, Room **2.8.4 via KSP 2.3.11**, Gradle **9.7.1**, compileSdk **37**, targetSdk **36**, minSdk **26**. SQLCipher **4.18.0** still uses `SupportOpenHelperFactory` on Room 2; do not jump to Room 3 / `SQLiteDriver` in the same pass. Do not set `jvmToolchain(17)` on this image (JDK 21 only); target 17 via `compilerOptions`. OkHttp **5.5.0** (`Response.body` is non-null; do not implement `Interceptor.Chain` in tests; ECH/DoH is opt-in — keep the default system DNS). JUnit 6 needs `junit-platform-launcher` on `testRuntimeOnly`. Prefs use Keystore AES-256-GCM (`com.pirlruc.finsilo.prefs_aes256_gcm`); do not add `security-crypto` / EncryptedSharedPreferences. Robolectric’s JVM has no `AndroidKeyStore` provider — unit tests inject a software AES-GCM AEAD; production `open()` constructs the Keystore AEAD eagerly so the widget still fail-closes. Vico **3.3.1** pie API is `pieModel { series(...) }` (`pieSeries` is deprecated). ktlint uses `android_studio` via `.editorconfig`. detekt is **2.0.0-alpha.6** (`dev.detekt`; do not apply `io.gitlab.arturbosch.detekt` — 1.23.8 calls deprecated `ReportingExtension.file`, and there is no 1.23.9). `CyclomaticComplexMethod` max is 10 (`allowedComplexity: 10`; 2.x is inclusive). Fail on Warning+ (`failOnSeverity`), not the 2.x Error default. CI pins `actions/setup-java` **v6.0.1**, `gradle/actions/wrapper-validation` **v6.3.0**, and `github/codeql-action` **v4.37.9**.

## Analog pins (TOOL-001 / O14)

| Companion | How it is pinned |
| --- | --- |
| `pirlruc/guardrails` | Git submodule SHA at `docs/guardrails/` (tag **1.6.0**, latest annotated release); `scripts/analog-pins.env` |
| `pirlruc/github-scaffold` | Git submodule SHA at `.github/scaffold/` (tag **1.5.0**, latest annotated release); templates synced into `.github/` and `.cursor/rules/` |
| `pirlruc/methodologies` | Cited by tag in docs (`tree/1.5.0/github-issue-adr`). Not a submodule. Folders: `docs/guardrails/`, `.github/scaffold/`. |

Private clones need `CURSOR_REPO_READ_TOKEN` (GitHub PAT) at VM start. `git submodule update --init` uses the public HTTPS URLs in `.gitmodules`.

Heimdall (`pirlruc/heimdall`) is an Android **SDK** stack (app → kit → core). FinSilo is an app: keep two Gradle modules (`:domain`, `:app`) and constructor injection. Do not copy Heimdall's kit/native layout.

## github-issue-adr

Authored backlog: [`docs/issues.yml`](issues.yml). Targets: [`docs/issues-sync-targets.yml`](issues-sync-targets.yml). Deviations (none): [`docs/guardrail-deviations.yml`](guardrail-deviations.yml). Do not invent `approved_by`. Analog 1.4.0 dropped numeric `doc_coverage` / `min_maintainability_index`; FinSilo keeps both as extra-strict gates in `config/kotlin.profile.thresholds.yml`. KT-SEC-004 is checksum-pinned **grype** on the Gradle CycloneDX BOM (OSV Scanner stays complementary).

Root wrappers call the submodule (do not vendor cppdevops CI):

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
bash scripts/sync-templates.sh
```

Issue content lives only in `docs/issues.yml`. Changing an `id` orphans the GitHub issue.

GitHub publish is **blocked in this VM**: `CURSOR_REPO_READ_TOKEN` is a fine-grained PAT with `issues=read` (`X-Accepted-GitHub-Permissions: issues=read; pull_requests=read`). `setup-issue-scaffold.sh` and `issues-sync.py` therefore 403 on label/milestone/issue create. Tracked as [TOOL-003](issues.yml). After the owner grants **Issues: Read and write** (and Contents remains as today), run:

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml
```

Then `--update` if rewriting bodies. Do not hand-create issues the manifest owns.

## What is still open after phases 1–6

Product leftovers (do not block calling 1–6 “shipped” except as noted):

- [FS-008](issues.yml) — kotlinx.serialization when a **third** JSON feed lands. Regex stays while the set is Frankfurter + AV + CoinGecko JSON plus Stooq CSV.
- Open value backlog: [FS-008](issues.yml) (typed JSON parsers, deferred), [FS-026](issues.yml) Trading 212 official API, [FS-033](issues.yml) medium scanner bars / SPDX allow-list, [FS-034](issues.yml) market TLS pinning, [FS-036](issues.yml) FX-before-first-rate, unsupported import fees, overlay-time alert redaction, and incremental NAV rebuild (T1 done in PR #20), [GATE-007](issues.yml) analog clone matching, [TOOL-003](issues.yml) GitHub issue publish. Analog pins stay on annotated tags **1.6.0 / 1.5.0** (latest releases; analog `main` is unreleased CI/docs only). Merged in [PR #15](https://github.com/pirlruc/finsilo/pull/15) (`bd0ddb1`): analog bump [TOOL-002](issues.yml), CI 1.4–1.6 gaps [GATE-005](issues.yml), analog-clone fallback and setup-android 4.0.4 [GATE-006](issues.yml), dead-code and lock/HTTP hardening [FS-032](issues.yml), picker privacy cover [FS-033-T1](issues.yml). Merged in [PR #18](https://github.com/pirlruc/finsilo/pull/18): lock hash/wrap unify [FS-033-T2](issues.yml), dashboard report fields [FS-033-T4](issues.yml), Room v8 unread nav columns [FS-035](issues.yml). Merged in [PR #20](https://github.com/pirlruc/finsilo/pull/20): manual capital, shared statement/AES-GCM paths, and hashed alert keys [FS-036-T1](issues.yml). Earlier leftovers already done: backup [FS-017](issues.yml), FIFO CSV/PDF [FS-018](issues.yml), NAV widget [FS-019](issues.yml), threshold alerts [FS-020](issues.yml), dual-currency holdings [FS-021](issues.yml), manual closes [FS-022](issues.yml), templates [FS-023](issues.yml), watchlist [FS-024](issues.yml), PIN-wrapped SQLCipher [FS-027-T2](issues.yml), Keystore AES-GCM prefs [FS-028](issues.yml), quote probe / biometric DB wrap / rating prefs / backup export [FS-030](issues.yml), first-purchase daily history / range dashboard loads / hybrid lock [FS-031](issues.yml). CSV import [FS-025](issues.yml) was already done. Do not reopen [FS-DEC-001](issues.yml). GitHub still has **zero issues** until [TOOL-003](issues.yml) (LIM-GH).

FS-030 follow-up in this branch: rating alerts ignore `NONE` priors; overlapping pickers keep `FLAG_SECURE` until depth 0; tax/backup CreateDocument writes tax CSV/PDF even when no backup name is pending; restore/quote HTTP bodies are size-capped; quote probes use compact AV/Stooq/CoinGecko windows and reuse a ticker cache on CSV review.

FS-031 in this branch: daily closes from first purchase are stored in full (about 0.06–0.1 MB/holding/year; not pruned or stepped — tens of MB for a 20-name decade is not material, and SMA-200/NAV need consecutive dailies). Charts draw every daily in the selected range; dashboard loads the selected HistoryRange; hybrid lock evicts SQLCipher after 15 minutes in the background.

Phase 7 quality/coverage/security gates are [GATE-001](issues.yml) (done, including Kover 95/95). Analog clone and detekt `@Composable` ignore leftovers are [GATE-003](issues.yml) (done). Gradle 9 `ReportingExtension.file` deprecation is [GATE-004](issues.yml) (done; detekt 2.x). Guardrails 1.6.0 CI alignment is [GATE-005](issues.yml) (done: ShellCheck, doc-links, analog pins, grype, SPDX license gate). Runner analog-clone fallback and setup-android 4.0.4 are [GATE-006](issues.yml) (done). Analog-versus-consumer key matching still waits on a renewed PAT ([GATE-007](issues.yml)).

Untracked limits (Semgrep registry, signing/release, emulator/SQLCipher, AV quota, GitHub Issues write): [limitations.md](limitations.md).

- Room `@Insert(OnConflictStrategy.REPLACE)` on `assets` is DELETE+INSERT, so SQLite CASCADE wipes lots, quotes, and price alerts. Asset writes use `@Upsert`.

## Market feed (GET only)

| Asset | Source | Key |
| --- | --- | --- |
| FX EUR/USD | Frankfurter latest + `from..to` history, then Alpha Vantage `CURRENCY_EXCHANGE_RATE` | AV optional |
| Crypto | CoinGecko `market_chart` | none |
| EU listings (`.DE`, …) | Stooq daily CSV, then AV | AV optional |
| Commodities | Stooq `https://stooq.pl/q/d/l/?s=xauusd` (trailing slash; `stooq.com` still allowlisted for 301); AV `WTI`/`BRENT`/… or XAU FX (spot-only does not replace a stored series) | AV for non-XAU |
| US stocks / ratings | AV `TIME_SERIES_DAILY` (full) + `OVERVIEW` (skipped if rating is newer than 7 days) | AV |
| Unlisted PPR | Local NAV (buy + interest); skipped on sync | — |
| Listed PPR | `quoteSymbol` / exchange suffix; routed like an ETF | — |

Sync skips names whose last stored bar date is already `asOf` and requests the rest oldest-first (never-quoted first) so one Alpha Vantage key is not wasted on fresh symbols. Watchlist `OVERVIEW` runs when the effective rating-alert set is non-empty (defaults are Buy / Strong Buy). Holding daily bars persist from the first BUY (else first lot date) and are not pruned (about 0.06–0.1 MB per holding per year). Dashboard reads only the selected `HistoryRange` window plus the latest bar per asset. Charts draw every daily in that window.

The OkHttp client refuses non-GET, non-allowlisted HTTPS hosts, and non-443 ports. Quote IOException text is host-only when the cause might include an Alpha Vantage query string. SMA is computed locally in `SyncMarketDataUseCase`. Quotes use `Asset.feedSymbol`. USD feeds (CoinGecko, AV US/commodities, Stooq XAU) stay USD and convert with stored EUR-per-USD even when the instrument is booked in EUR (`QuoteCurrency`). Execution FX on a USD trade does **not** overwrite `currency_history` when that date already has a row ([FS-014](issues.yml)). Empty FX history omits USD-quoted holdings from NAV and surfaces a dashboard warning ([FS-005](issues.yml)).

## Security

- SQLCipher wraps, optional Alpha Vantage key, PIN hashes, and widget NAV in Keystore AES-256-GCM SharedPreferences (`SecurePreferences`). PIN hashes and SQLCipher wraps share `finsilo_credentials` so setup, recovery PIN reset, wrap upgrade, and recovery rotation are one `commit()` ([FS-033-T2](issues.yml)). Fake lock tests still use the two-step path with `rollbackLastWrap`.
- First-launch PIN (4–8 digits), optional biometrics, and a one-time recovery code that resets the PIN. Recovery cannot reconstruct the PIN. PIN/recovery hashes use PBKDF2-HMAC-SHA256 at 210k iterations (off the main thread). Five failed PIN/recovery attempts start a 30s lockout that doubles, cap 15 minutes. Settings changes to biometrics or the recovery code require the current PIN. The session re-locks when the **process** goes to the background (`ProcessLifecycleOwner` `ON_STOP`; skipped while the biometric prompt or an external picker is showing). Relock overlays the PIN screen so in-progress ledger fields are kept. After **15 minutes** still in the background, Room is closed and the SQLCipher session key is wiped even if the overlay was skipped for a picker or biometric sheet; returning to the PIN screen before that keeps the session. A wrap-upgrade confirm that is still open is aborted on that eviction so the user is not stuck without a session. Night sync no-ops when the session is closed. An empty Unlock tap does not count as a failed attempt. Recovery code screens show a copy icon beside the code; the clipboard is cleared after 60s via a main-looper `Handler` (`FLAG_SECURE` stays on the activity except while a document picker is open).
- SQLCipher passphrase parsing uses the same hex decoder as the lock (corrupt prefs fail closed; they do not throw `NumberFormatException`). PIN and recovery wrap the 32-byte key; a third wrap is Keystore AES-GCM with `setUserAuthenticationRequired(true)`, `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` on API 30+, and `setInvalidatedByBiometricEnrollment(true)`. The AES wrap key is copied before the PBKDF2 buffer is wiped. Cold start can unwrap with biometrics; **Use PIN** or failure falls back to PIN. Biometric unwrap copies the key before the prompt callback zeros it. `BiometricPrompt` is created after composition (it commits a fragment). Unlock opens the SQLCipher database before the ledger UI is shown; a failed open stays on the PIN screen. Enrollment change shows “Biometrics changed. Enter PIN.” then reseals. Backup files are FSILO-LEDGER-3 (rating alert rows); decode still accepts v1/v2. Backup TSV unescape is left-to-right so `\\n` in a name is not turned into a newline.
- Room v2→v9 schema changes are `AutoMigration` (no `execSQL` in `src/main`). v9 adds optional `broker_source` on `transactions`. Scanner scope filters are listed in [`docs/scanner-exceptions.md`](scanner-exceptions.md); there are no finding-level ignores.
- `FLAG_SECURE` and `filterTouchesWhenObscured` on the main activity (no screenshots of the ledger/PIN; ignore overlay taps). While a document picker is open the flag is cleared after an opaque cover is composed; it is restored before the cover is removed. Ledger nodes are hidden from accessibility while that cover is up. Alert dedup keys in `finsilo-alerts` are SHA-256 digests (`AlertPublishKey`); the lock-screen public version says “Portfolio alert” with no symbol or price. Shade text while the in-app overlay is up is [FS-036-T4](issues.yml).
- Hash/wrap dual-write uses compensating rollback (`rollbackLastWrap`) only on the fake/two-store test path. Production uses one `finsilo_credentials` file ([FS-033-T2](issues.yml)).
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
| **OSV Scanner** (new) | Dependency vulns (OSV.dev) against the Gradle CycloneDX BOM (`cyclonedxBom`, runtime classpaths) | Complements PR-only dependency-review. Local: `scripts/run-osv-scanner.sh`. |
| **grype** (GATE-005) | KT-SEC-004 dependency vuln scan on the same CycloneDX BOM | Named org tool; OSV stays complementary. Local: `scripts/run-grype.sh`. |
| **SPDX license gate** (GATE-005) | SC-LIC-001 against analog `license_deny_list` / `license_allow_list` | Org defaults are empty lists (gate runs, denies nothing) until a release allow-list is filled. |
| **MobSF mobsfscan** (new) | Mobile Security Framework source SAST | Not previously included. Full MobSF Docker APK analysis is not in CI (no Docker in this flow); mobsfscan is the CI-practical MobSF gate. |

## Sample data

`SamplePortfolioFactory` is deterministic synthetic data (not market data). Includes AAPL (USD), VWCE.DE, BTC, unlisted PPR (ISIN on the asset row, interest stays in NAV), CT, deposit, XAU commodity, and three AAPL dividends for YOC. Loaded only from the empty-state button. AAPL’s last sample bar is forced through a golden cross for demo only ([FS-011](issues.yml)). Unlisted PPR has no invented daily quotes. Live sync and notifications use stored SMAs only.

## Recent history

Unlock crash fix: biometric Keystore keys require strong biometrics on every use (API 30+), `BiometricPrompt` is created after composition, and a SQLCipher open failure during PIN or biometric unlock stays on the lock screen instead of crashing the ledger. The PBKDF2 buffer is wiped only after the AES wrap key is copied.

Review pass (merged in [PR #20](https://github.com/pirlruc/finsilo/pull/20)): manual ledger rows show on the capital card as `BrokerSource.MANUAL` (not stored on transactions). Trading 212 and Revolut statement rows, DEGIRO holding drafts, CSV header lookup, Alpha Vantage GETs, ledger dropdowns, and capital-card lines share helpers. SQLCipher wraps and backups share `AesGcmPassphrase`. Alert dedup prefs store a SHA-256 digest, and the lock-screen public notification has no holding text. Guardrails pin stays **1.6.0** (latest annotated tag); `docs/guardrail-deviations.yml` stays empty. Scanner finding suppressions stay empty; scope filters in [scanner-exceptions.md](scanner-exceptions.md) were not widened. Medium fail bars remain [FS-033-T3](issues.yml).

Broker CSV follow-up (main, PR #19): DEGIRO/Revolut locale + split cash columns so later imports work; cash-sweep `INTEREST` vs deposits; dashboard capital card (money put in, leftover cash, interest, signed gain/loss per `BrokerSource`); Room v9 `broker_source`; Stooq daily CSV on `stooq.pl/q/d/l/` with redirects for XAU 301s. Per-broker leftover cash/lots come from a **global FIFO walk** (`CapitalAttributor`) so a later import that spends another broker’s EUR-CASH does not invent a phantom gain. Import fingerprints include `BrokerSource` (v8 untagged deposits still match a later interest row). Kover still 95/95 on `:domain`. Android UI was not emulator-checked in this pass.

*Last updated: 2026-09-24 (unlock crash: biometric CryptoObject + SQLCipher open)*
