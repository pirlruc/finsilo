# FinSilo

On-device Android tracker for a mixed portfolio: stocks, ETFs, crypto, Portuguese PPR, deposits, Certificados de Tesouro, cash, and commodities. FIFO cost basis, EUR valuation, and charts stay on the phone. Quotes are optional GET-only lookups — there is no account at a broker and no cloud ledger.

Requires **Android 8** or later. This is a personal tool, not tax advice or a broker.

## First launch

1. Choose a **PIN** (4–8 digits). Optional: turn on **biometrics**.
2. Write down the **recovery code**. It resets the PIN; it cannot reconstruct the PIN. Copy is allowed; the clipboard is cleared after 60 seconds if it still holds the code.
3. After a cold start you can unlock with biometrics when enrolled. **Use PIN** if biometrics fail or after you add a new fingerprint/face (then confirm once so the ledger key can be wrapped again).

The app shows the PIN screen when you leave it. A document picker or the biometric prompt will not count as leaving. If it stays in the background for **15 minutes**, the database key is wiped and you unlock into a fresh session. Coming back to the PIN screen before that keeps the open ledger in memory.

## Daily use

- **Dashboard** — allocation pie, NAV history for the selected chip (1M / 3M / YTD / All), TWR, yield on cost, ratings, and SMA 50/200 from stored closes.
- **Add a transaction** — Buy, Sell, Deposit, Withdrawal, Dividend, Interest. Sells above remaining FIFO quantity and withdrawals above cash are refused. A buy that needs more cash than you have books a same-day deposit first. Unlisted PPR interest stays in NAV. Selling CT, a deposit, or unlisted PPR is a redemption.
- **ISIN / quote symbol** — optional on the instrument. Changing the quote symbol drops that holding’s stored daily bars and refetches on the next sync.
- **Watchlist** — followed symbols only. They never change FIFO, TWR, or the pie.
- **Settings** — target weights (must sum to 100%), lock/recovery, tax CSV/PDF, encrypted backup, broker CSV import.

A **synthetic sample portfolio** is on the empty dashboard so you can review charts with no network. It is not market data and is not a backup.

## Quotes

Sync is optional. Without a key, Frankfurter (EUR/USD, including history), Stooq (many EU listings and gold), and CoinGecko (crypto) still work.

An **Alpha Vantage** key (free tier is about 25 calls/day) lives under the dashboard menu **Alpha Vantage key**. It stays on-device and is used for US names, ratings, some commodities, and as a fallback. The app skips symbols that already have today’s close and requests the rest oldest-first so one key is not wasted on fresh names.

Stored daily closes start at each holding’s first buy and stay daily for the whole holding life (a decade of quotes is a few megabytes, so the file is not pruned and the NAV chart is not stepped). The dashboard only reads quotes for the selected chip (plus the latest close per name).

New mark-to-market buys and watchlist adds are checked against the feed first. Deposit, CT, and unlisted PPR are valued locally and are not probed.

USD quotes stay USD and convert with stored EUR-per-USD even when the instrument is booked in EUR.

## Broker CSV

From the empty dashboard or Settings, import Trading 212 History, DEGIRO Transactions + Account statement, or Revolut Stocks account statement.

Lots always persist. You can edit quote symbols on the review screen if a ticker has no live close; those names still import, with a warning. Live Trading 212 API sync is not built.

## Alerts

After the 23:00 sync or a manual Sync:

- **Price** — per holding, EUR level and/or day-move percent on stored closes.
- **Rating** — per holding (defaults: Sell and Strong Sell) and per watchlist row (defaults: Buy and Strong Buy). A missing preference uses those defaults. Saving an empty set notifies none. Notifications fire when the latest rating **enters** the selected set, not on every change, and not when the previous bar has no real rating yet.

Notifications need the usual Android permission on API 33+.

## Backup and tax export

Encrypted backup (`.fsi`) is wrapped with **this install’s recovery code**. After unlock on a new phone, type the code that was used to encrypt the file — it can differ from the new phone’s lock recovery. Restore asks before overwriting the live ledger, watchlist, templates, and alerts. Canceling the system file picker does not write a file.

Plus-valias CSV/PDF is calendar-year FIFO in stored EUR. Redemptions are labeled Resgate. It is not a full IRS pack. You pick the destination after Export CSV / Export PDF.

## Home screen widget

Optional NAV widget. It only updates from on-device data after unlock/sync.

## Privacy

- Ledger is SQLCipher. PIN, recovery, and optional biometrics wrap the database key. Leaving the app shows PIN immediately; after 15 minutes in the background the in-memory key is wiped. Preference secrets use Keystore AES-GCM.
- Screenshots of the ledger and PIN are blocked (`FLAG_SECURE`), except while a system file picker is open.
- No cloud backup of the database (`allowBackup=false`).
- Network is HTTPS GET only to Frankfurter, Alpha Vantage, CoinGecko, and Stooq.

## Build from source

JDK 17+, Android SDK (compile 37 / target 36). `local.properties` is gitignored; set `sdk.dir` or `ANDROID_HOME`.

```bash
./gradlew :app:assembleDebug
./gradlew :domain:test :domain:koverVerify :app:testDebugUnitTest
bash scripts/ci-local.sh
```

Contributor process, analog pins, and the authored backlog live in [`docs/ai-agent-handoff.md`](docs/ai-agent-handoff.md), [`docs/issues.yml`](docs/issues.yml), and [`docs/limitations.md`](docs/limitations.md).
