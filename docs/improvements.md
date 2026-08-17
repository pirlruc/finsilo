# Improvements

Product decisions from the RFC follow-up are recorded first. The living backlog is
[`docs/issues.yml`](issues.yml), synced to GitHub with
[github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr)
(`Epic: [ID] …` / `Task: [ID] …`). Do not reopen decided items unless the user
explicitly changes them.

RFC phases: **1** foundation (Room/SQLCipher) · **2** data entry · **3** market APIs · **4** calc engine · **5** notifications · **6** dashboard.

Sync:

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml
```

Those commands 403 until a PAT with **Issues: Read and write** is available (`issues=read` is not enough to create labels, milestones, or issues). The authored manifest is still the source of truth.

## Decided

1. **FX quote direction.** EUR per 1 USD. USD amounts convert as `native * eurPerUsd`. Column: `currency_history.eur_usd_rate`.
2. **Uninvested cash in the pie.** Keep a Cash slice. Portuguese CT/deposit interest stays inside those instruments. Slices remain separated by investment type.
3. **FIFO lots.** Open cost is FIFO (oldest buy consumed first), not a moving average.
4. **Quotes.** Free APIs only, even with rate limits. Routing: Frankfurter (FX), CoinGecko (crypto), Stooq (EU listings and XAU), Alpha Vantage (US daily, OVERVIEW ratings, commodity series, FX fallback). Unofficial Yahoo JSON is out. SMA 50/200 are computed on-device from stored closes.
5. **COMMODITY.** Mark-to-market versus FIFO buy cost, like stocks. Alpha Vantage commodity / XAU endpoints plus Stooq `xauusd`.
6. **TWR cashflows.** Sub-periods open only on (a) a buy whose cost exceeds uninvested cash (external funding) and (b) withdrawals. `DEPOSIT_CASH` alone, internal buys, dividends, and interest do not split.
7. **YOC.** Both TTM and last payment × inferred frequency (1 / 2 / 4 / 12 from the TTM payment count).
8. **minSdk 26.** Unchanged.
9. **Guardrails / methodologies / github-scaffold.** Pin with `CURSOR_REPO_READ_TOKEN`. Analog: [heimdallcv](https://github.com/pirlruc/heimdallcv). Methodologies is cited by tag, not vendored as a submodule. Do not invent fake submodules.

## Issue map (RFC leftovers → github-issue-adr)

| RFC | Epic | Milestone | Status |
| --- | --- | --- | --- |
| O1 | [FS-001](issues.yml) Compose ledger forms | Phase 2 | **done** |
| O2 | [FS-002](issues.yml) FIFO oversell | Phase 4 (UI guard done) | **open** (`FS-002-T2`) |
| O3 | [FS-003](issues.yml) Cash overdraft | Phase 4 (UI guard done) | **open** (`FS-003-T2`) |
| O4 | [FS-004](issues.yml) Target settings | Phase 2 | **done** |
| O5 | [FS-005](issues.yml) FX `= 1` fallback | Phase 3 | **open** |
| O6 | [FS-006](issues.yml) PPR listed vs unlisted | Phase 2 + 3 | **open** (`FS-006-T3` listed routing) |
| O7 | [FS-007](issues.yml) XAU spot vs series | Phase 3 | **open** |
| O8 | [FS-008](issues.yml) Parser robustness | Phase 3 | **open** |
| O9 | [FS-009](issues.yml) Alpha Vantage quota | Phase 3 | **open** |
| O10 | [FS-010](issues.yml) TWR/NAV cache | Phase 4 | **open** |
| O11 | [FS-011](issues.yml) Sample vs live honesty | Phase 6 | **open** (`FS-011-T2`) |
| O12 | [FS-012](issues.yml) Room migrations | Phase 1 | **open** (`FS-012-T2`) |
| O13 | [FS-013](issues.yml) Notifications | Phase 5 | **open** |
| O14 | [TOOL-001](issues.yml) Analog pins + github-issue-adr | Tooling | **done** |

New findings from the second pass (also in `docs/issues.yml`):

| Epic | Issue | Status |
| --- | --- | --- |
| [FS-014](issues.yml) | Typed execution FX must not REPLACE `currency_history` for that date | **done** |
| [FS-015](issues.yml) | Asset + transaction + FX seed in one Room `@Transaction` | **done** |
| [FS-016](issues.yml) | European/US decimal parse (`1.234,56` / `1,234.56`) | **done** |
| [GATE-001](issues.yml) | Kotlin quality, coverage, and security gates | **open** (`GATE-001-T1` domain-test CI **done**) |

## Suggested order

1. **TOOL-001** analog pins and issue sync (done).
2. **Phase 2:** FS-001, FS-002-T1, FS-003-T1, FS-004, FS-006-T1/T2, FS-014, FS-015, FS-016 (done).
3. **Phase 3:** FS-005, FS-007, FS-009, FS-006-T3 (FS-008 when a parser next breaks).
4. **Phase 5:** FS-013 (FS-011 live path must stay honest).
5. **Phase 4 / Phase 1 remainder:** FS-010, FS-002-T2, FS-003-T2, FS-012-T2.
6. **GATE-001-T2…T4** ktlint/detekt, Kover, secret/SAST — do not record a fake lowered-gate deviation.

## Known limitations

- Missing FX history falls back to `eurPerUsd = 1`, which mis-values USD assets ([FS-005](issues.yml)).
- NAV history is O(days × holdings). Fine for a few years; downsample already kicks in above 180 points for the chart only ([FS-010](issues.yml)).
- Alpha Vantage free tier is about 25 calls/day. Sync skips a symbol rather than retrying against unofficial feeds ([FS-009](issues.yml)).
- Gold history without a key depends on Stooq `xauusd`. Alpha Vantage XAU is a spot exchange rate (one bar), not a full series ([FS-007](issues.yml)).
- Sample SMA 200 is shortened when fewer than 200 closes exist; AAPL’s last bar is forced through a golden cross so the dashboard can demonstrate the signal. That is demo-only, not a market event ([FS-011](issues.yml)).
