# Improvements

Product decisions from the RFC follow-up are recorded first. Open issues include the **phase** they belong in and a **recommended solution**. Do not reopen decided items unless the user explicitly changes them.

RFC phases: **1** foundation (Room/SQLCipher) · **2** data entry · **3** market APIs · **4** calc engine · **5** notifications · **6** dashboard.

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

## Open issues

| ID | Issue | Phase | Recommendation / status |
| --- | --- | --- | --- |
| **O1** | No Compose forms for Buy / Sell / Deposit / Withdrawal / interest. Dashboard can only load the synthetic sample. | **2** | **Done this slice.** One ledger form per `TransactionType`, writing through `RoomPortfolioRepository`. Validates quantity, native price, EUR conversion (`native * eurPerUsd` for USD), and fees. Reuses `GetDashboardUseCase` after save. |
| **O2** | Selling more than remaining FIFO quantity still credits the **full sell cash** while lots go to zero. | **2** (block in UI); **4** (ledger guard) | **Phase 2 done.** `RecordLedgerEntryUseCase` refuses the save when `quantity > PositionLedger.position(...).quantity`. Phase 4: make `consumeFifo` return unfilled qty and credit cash only for filled quantity. |
| **O3** | Withdrawal larger than uninvested cash makes **cash negative**. Pie omits a negative cash slice; total NAV still includes it. | **2** (block in UI); **4** (policy) | **Phase 2 done.** Withdrawals above `cashEur` are refused. Phase 4: keep the ledger from going negative unless you later add an explicit “overdraft / external” type. Do not hide negative cash only in the pie. |
| **O4** | Target weights are seeded in the sample but there is no settings UI. Drift (±5%) is computed against whatever is in `target_allocation`. | **2** | **Done this slice.** Settings form: one percent field per `AssetType`, sum must be 100, persist via `replaceTargets`. Same ±5% band (`exceedsDriftBand`). |
| **O5** | Empty FX history still falls back to **`eurPerUsd = 1`**, which mis-values USD holdings. A *present* later quote is already carried backward; sync already stores a Frankfurter `from..to` range. | **3** | After a successful sync, require at least one FX row before valuing USD assets. If FX fetch fails, keep last stored rates and surface the failure on the dashboard. Only use `1.0` for EUR-only portfolios. |
| **O6** | Unlisted **PPR** symbols (no exchange suffix) are skipped on sync so they do not burn Alpha Vantage quota. Sample PPR still has synthetic prices. | **2** + **3** | **Phase 2 fields done.** `Asset.isin` and `Asset.quoteSymbol` are stored separately from the display name. Sync uses `feedSymbol` and skips PPR when `quoteSymbol` is blank and the feed symbol has no exchange suffix. Phase 3: if the user supplies a listed ticker, route it like an ETF (Stooq then AV). If not, treat PPR like a locally valued fund NAV entered by the user (same pattern as CT interest). |
| **O7** | Gold via Alpha Vantage XAU is a **spot bar**, not a series. History without a key depends on Stooq `xauusd`. | **3** | Keep Stooq `xauusd` as the history source. Use AV `CURRENCY_EXCHANGE_RATE` only as a same-day fallback when Stooq is empty. Do not replace a stored XAU series with a single spot bar on sync. |
| **O8** | Quote parsing is **regex over JSON/CSV**. Fragile if a provider changes payload shape. | **3** | Keep regex while the feed set is small. When adding a third AV function or a new provider, add `kotlinx.serialization` (or Moshi) in `:domain` for JSON only; keep Stooq as CSV. Do not pull an unofficial Yahoo client. |
| **O9** | Alpha Vantage free tier is ~**25 calls/day**. `TIME_SERIES_DAILY` full + `OVERVIEW` per US name will exhaust it quickly. | **3** | Sync US names sequentially; persist last-success per `asset_id`; skip `OVERVIEW` unless the last rating row is older than 7 days. Prefer Stooq/CoinGecko/Frankfurter first so AV is only for US listings and commodities that have no free series. Quota `Note` payloads already fail that symbol. |
| **O10** | TWR is **O(transactions × full NAV rebuild)**. History is **O(days × holdings)**. Fine for a few years, not for a decade of daily points plus many lots. | **4** | Cache `totalNavEur` by date in memory for one `GetDashboardUseCase` call (history, TWR, and allocation share it). Later, persist a `nav_history` table rebuilt only when the ledger or quotes change. Do not change TWR split rules (external buy + withdrawal only). |
| **O11** | Sample AAPL SMA 200 is shortened when fewer than 200 closes exist, and the last bar is **forced through a golden cross**. | **6** now (demo only); **5** when notifications ship | Leave the force in `SamplePortfolioFactory` but keep it commented as demo-only. Live sync must never invent a cross. Phase 5 notifications should use `GetMarketSignalsUseCase` on stored SMAs only. |
| **O12** | Room used destructive migration on schema change (v2). Alpha is OK; real user ledgers are not. | **1** before first non-sample install | **Started this slice.** v3 adds `isin` / `quote_symbol` via `MIGRATION_2_3`. `fallbackToDestructiveMigrationFrom(1)` only. Export schema and stop remaining fallback before a non-sample install ships. |
| **O13** | Phase 5 notifications are not implemented. Signal math already exists on the dashboard. Daily quote `WorkManager` at 23:00 already exists. | **5** | Reuse `GetMarketSignalsUseCase` and `exceedsDriftBand`. Fire after the existing 23:00 sync worker succeeds. Channels: rating change, golden/death cross, allocation drift. No extra network in the notification path. |
| **O14** | Private `methodologies`, `guardrails`, and `github-scaffold` were unpinned. | **Now** (tooling, not a product phase) | **Done this slice.** `CURSOR_REPO_READ_TOKEN` was present (length 93, `github_pat_` prefix). Cloned `pirlruc/methodologies` (cite only), pinned `docs/guardrails` @ `1.3.0` and `.github/scaffold` @ `1.2.0`. Synced templates from the scaffold. Did not invent templates. |

## Suggested order

1. **O14** if the token is in this VM (done).
2. **Phase 2:** O1 + O2 + O3 + O4 and O6 fields (done).
3. **Phase 3 hardening:** O5, O7, O9 (O8 only when a parser next breaks). O6 listed-PPR routing.
4. **Phase 5:** O13 (O11 live path must stay honest).
5. **Phase 4 cache / Phase 1 migrations:** O10, O12 remainder when real data exists.

## Known limitations

- Missing FX history falls back to `eurPerUsd = 1`, which mis-values USD assets (O5).
- NAV history is O(days × holdings). Fine for a few years; downsample already kicks in above 180 points for the chart only.
- Alpha Vantage free tier is about 25 calls/day. Sync skips a symbol rather than retrying against unofficial feeds.
- Gold history without a key depends on Stooq `xauusd`. Alpha Vantage XAU is a spot exchange rate (one bar), not a full series.
- Sample SMA 200 is shortened when fewer than 200 closes exist; AAPL’s last bar is forced through a golden cross so the dashboard can demonstrate the signal. That is demo-only, not a market event.
