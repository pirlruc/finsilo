# Improvements

Open product and architecture questions. Please answer these; several already have a working default in code.

## Please confirm or correct

1. **FX quote direction.** Code stores `usdPerEur` (USD per 1 EUR). EUR value of a USD amount is `native / usdPerEur`. Should this instead be EUR per USD?
2. **Uninvested cash in the pie.** `DEPOSIT_CASH` minus buys/sells/fees plus marketable dividends is a Cash slice. Should cash be hidden, or should Portuguese CT/deposit interest stay inside those instruments (current behavior)?
3. **Average cost vs FIFO.** Sells reduce remaining cost proportionally (moving average). Portuguese tax reporting often wants FIFO lots. Should lot-level FIFO be the ledger?
4. **Yahoo unofficial endpoints.** Phase 3 currently should **not** scrape unofficial Yahoo JSON. Preferred alternative: Alpha Vantage only, Stooq, Twelve Data, or user-imported files?
5. **COMMODITY.** In the enum, no valuation rule. Treat like stocks (needs a price feed) or like deposits?
6. **TWR cashflows.** RFC splits only on `DEPOSIT_CASH`. Should buys funded from external cash, withdrawals, and dividends also open TWR sub-periods?
7. **YOC annualization.** No method specified (TTM dividends, last payment × frequency, user flag). Which one?
8. **minSdk 26.** Vico allows 23; 26 was chosen for `java.time` and Keystore defaults. Need 23/24?
9. **Guardrails / methodologies / github-scaffold** are private (404 here). Grant access or vendor a snapshot so lint/issue templates can be pinned.

## Suggested next slices

- Phase 2 transaction entry, including ISIN/Yahoo symbol for PPR and a CT/deposit interest form.
- Phase 3 GET-only sync behind a user-supplied Alpha Vantage key in EncryptedSharedPreferences; skip unofficial Yahoo until a source is chosen.
- Phase 5 `WorkManager` at 23:00 that reuses `GetMarketSignalsUseCase` and allocation drift (`exceedsDriftBand`) for notifications.
- YOC + TWR use cases once (6) and (7) are answered.
- Settings screen to edit `target_allocation` weights (they must sum to 100).

## Known limitations

- Missing FX history falls back to `usdPerEur = 1`, which mis-values USD assets. Prefer blocking valuation over a silent 1.0 once sync exists.
- NAV history is O(days × holdings). Fine for a few years; downsample already kicks in above 180 points for the chart only.
- Sample SMA 200 is shortened when fewer than 200 closes exist; AAPL’s last bar is forced through a golden cross so the dashboard can demonstrate the signal. That is demo-only, not a market event.
