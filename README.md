# FinSilo

Privacy-first, offline-first Android tracker for multi-asset portfolios (stocks, ETFs, crypto, PPR, deposits, Portuguese Certificados de Tesouro, and commodities). Values, gains, and charts stay on-device.

This repository currently delivers product phases **1–6**. Phase **7** wires the guardrail workflows that can run on this public repo (quality including domain+app maintainability index, Android lint/assemble/Robolectric, gitleaks, semgrep, dependency-review, Dokka/KDoc, CycloneDX SBOM). Kover branch coverage is still below 95% — see [`docs/limitations.md`](docs/limitations.md).

## What works now

- Encrypted Room/SQLCipher ledger (assets, transactions, daily market history, FX, target allocation, persisted `nav_history`).
- First-launch **PIN** (optional biometrics) and a one-time **recovery code** that resets the PIN.
- Compose **ledger entry** for Buy / Sell / Deposit / Withdrawal / Dividend / Interest on every investment type (cash uses deposit/withdrawal). Optional ISIN and quote symbol (PPR). Sells above remaining FIFO quantity and withdrawals above uninvested cash are refused. Unlisted PPR interest stays in NAV. Sell on CT/deposit/unlisted PPR is a redemption.
- Settings for `target_allocation` weights (must sum to 100) and lock/recovery rotation.
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
| Quotes | Free GET-only feeds only. **No unofficial Yahoo JSON.** Alpha Vantage (optional key, ~25 calls/day), Frankfurter FX, Stooq EU/XAU, CoinGecko crypto. USD feeds convert with EUR-per-USD even when the instrument is booked in EUR. SMA 50/200 are computed locally so AV quota is not spent on SMA. |
| Commodities | Mark-to-market: current price versus FIFO buy cost, same as stocks. XAU via Stooq (`xauusd`) or Alpha Vantage `CURRENCY_EXCHANGE_RATE`; WTI/BRENT/… via Alpha Vantage commodity series. |
| TWR splits | Only **buys funded with external cash** (cost exceeds uninvested cash) and **withdrawals**. `DEPOSIT_CASH`, internal buys, dividends, and interest do not open a sub-period. |
| YOC | Both **TTM** (dividends in the last 365 days / remaining FIFO cost) and **last payment × inferred frequency** (1, 2, 4, or 12 from the TTM count). |
| minSdk | **26** (java.time + Keystore defaults). |
| Guardrails / methodologies / github-scaffold | Private analog repos. `docs/guardrails` @ **1.3.0**, `.github/scaffold` @ **1.2.0**. Methodologies is cited ([github-issue-adr @ 1.2.0](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr)), not vendored. |

## Architecture

Clean architecture, two Gradle modules:

- `:domain` — pure Kotlin (JVM). Valuation, FIFO ledger, allocation, history, TWR, YOC, signals, feed parsers, ledger-entry validation. Covered by JUnit 5.
- `:app` — Compose UI, Room, SQLCipher, Vico, WorkManager (23:00 daily sync + rating/cross/drift notifications), OkHttp GET-only client.

No Hilt. Constructor injection from `AppContainer` keeps the first slice small and testable.

## Build

Requirements: JDK 17+, Android SDK 35/36.

```bash
bash scripts/ci-local.sh
./gradlew :domain:koverVerify   # expected red until LIM-COV / GATE-001-T3
bash scripts/issues-sync.sh --validate-only
```

`local.properties` is gitignored. Point `sdk.dir` or `ANDROID_HOME` at your SDK.

Private submodules (`docs/guardrails`, `.github/scaffold`) need GitHub access. After clone:

```bash
git submodule update --init
```

The app requests **INTERNET** for GET-only quote sync. Cleartext is disabled.

## Methodology

Living docs follow the public [heimdallcv](https://github.com/pirlruc/heimdallcv) analog and [github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr):

- [`docs/ai-agent-handoff.md`](docs/ai-agent-handoff.md)
- [`docs/issues.yml`](docs/issues.yml) — authored Epic/Task backlog (source of truth)
- [`docs/improvements.md`](docs/improvements.md) — index into `docs/issues.yml`

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
```

Creating labels, milestones, and issues needs a GitHub token with **Issues: Read and write**. A Contents-only or `issues=read` token 403s.
