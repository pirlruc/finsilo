# Improvements

Product decisions from the RFC follow-up are recorded here. Remaining work is listed after that.

## Decided

1. **FX quote direction.** EUR per 1 USD. USD amounts convert as `native * eurPerUsd`. Column: `currency_history.eur_usd_rate`.
2. **Uninvested cash in the pie.** Keep a Cash slice. Portuguese CT/deposit interest stays inside those instruments. Slices remain separated by investment type.
3. **FIFO lots.** Open cost is FIFO (oldest buy consumed first), not a moving average.
4. **Quotes.** Free APIs only, even with rate limits. Routing: Frankfurter (FX), CoinGecko (crypto), Stooq (EU listings and XAU), Alpha Vantage (US daily, OVERVIEW ratings, commodity series, FX fallback). Unofficial Yahoo JSON is out. SMA 50/200 are computed on-device from stored closes.
5. **COMMODITY.** Mark-to-market versus FIFO buy cost, like stocks. Alpha Vantage commodity / XAU endpoints plus Stooq `xauusd`.
6. **TWR cashflows.** Sub-periods open only on (a) a buy whose cost exceeds uninvested cash (external funding) and (b) withdrawals. `DEPOSIT_CASH` alone, internal buys, dividends, and interest do not split.
7. **YOC.** Both TTM and last payment × inferred frequency (1 / 2 / 4 / 12 from the TTM payment count).
8. **minSdk 26.** Unchanged.
9. **Guardrails / methodologies / github-scaffold.** Use `CURSOR_REPO_READ_TOKEN` to clone and pin. The token is not in this environment yet (requested as an environment secret). Do not invent fake submodules.

## Suggested next slices

- Phase 2 transaction entry, including ISIN/symbol for PPR and a CT/deposit interest form.
- Settings screen to edit `target_allocation` weights (they must sum to 100).
- Phase 5 `WorkManager` notifications that reuse `GetMarketSignalsUseCase` and allocation drift (`exceedsDriftBand`). Daily quote sync at 23:00 already exists.
- Pin `docs/guardrails/` and `.github/scaffold/` once `CURSOR_REPO_READ_TOKEN` is available.

## Known limitations

- Missing FX history falls back to `eurPerUsd = 1`, which mis-values USD assets. Prefer blocking valuation over a silent 1.0 once sync is the default path.
- NAV history is O(days × holdings). Fine for a few years; downsample already kicks in above 180 points for the chart only.
- Alpha Vantage free tier is about 25 calls/day. Sync skips a symbol rather than retrying against unofficial feeds.
- Gold history without a key depends on Stooq `xauusd`. Alpha Vantage XAU is a spot exchange rate (one bar), not a full series.
- Sample SMA 200 is shortened when fewer than 200 closes exist; AAPL’s last bar is forced through a golden cross so the dashboard can demonstrate the signal. That is demo-only, not a market event.
