# FinSilo

On-device Android tracker for a mixed portfolio: stocks, ETFs, crypto, Portuguese PPR, deposits, Certificados de Tesouro, cash, and commodities. FIFO cost basis, EUR valuation, and charts stay on the phone. Quotes are optional GET-only lookups — there is no account at a broker and no cloud ledger.

Requires **Android 8** or later. This is a personal tool, not tax advice or a broker.

## First launch

1. Choose a **PIN** (4–8 digits). Optional: turn on **biometrics**.
2. Write down the **recovery code**. It resets the PIN; it cannot reconstruct the PIN. Copy is allowed; the clipboard is cleared after 60 seconds.
3. After a cold start you can unlock with biometrics when enrolled. **Use PIN** if biometrics fail or after you add a new fingerprint/face (then confirm once so the ledger key can be wrapped again).

The app shows the PIN screen when you leave it. A document picker or the biometric prompt will not count as leaving. If it stays in the background for **15 minutes**, the database key is wiped and you unlock into a fresh session. Coming back to the PIN screen before that keeps the open ledger in memory.

## Daily use

- **Overview** — NAV, net invested, return, TWR, XIRR, a VWCE / S&P 500 comparison when stored history exists (a comparison, not a promised return), history chips (1M / 3M / 6M / 1Y / YTD / All), capital by broker, and broker CSV import.
- **Add a transaction** — Buy, Sell, Deposit, Withdrawal, Dividend, Interest. Sells above remaining FIFO quantity and withdrawals above cash are refused. A buy that needs more cash than you have books a same-day deposit first. Unlisted PPR interest stays in NAV. Selling CT, a deposit, or unlisted PPR is a redemption.
- **ISIN / quote symbol** — optional on the instrument. Changing the quote symbol drops that holding’s stored daily bars and refetches on the next sync.
- **Watchlist** — followed symbols only. They never change FIFO, TWR, or the pie.
- **Holdings** — search, sort, FIFO lots, dividends, and alerts.
- **Activity** — ledger rows with edit, delete, and a date picker.
- **Insights** — allocation, rebalance amounts, yield on cost, signals, a dividend calendar with forward 12-month income, an IRS annual summary (not tax advice and not the official XML), and an in-app Mais-Valias preview.
- **Settings** — target weights (must sum to 100%), lock/recovery, tax CSV/PDF, and encrypted backup.

A **synthetic sample portfolio** is on the empty dashboard so you can review charts with no network. It is not market data and is not a backup.

## Quotes

Sync is optional. Without a key, Frankfurter (EUR/USD, including history), Stooq (many EU listings and gold), and CoinGecko (crypto) still work.

An **Alpha Vantage** key (free tier is about 25 calls/day) lives under the dashboard menu **Alpha Vantage key**. It stays on-device and is used for US names, ratings, some commodities, and as a fallback. The app skips symbols that already have today’s close and requests the rest oldest-first so one key is not wasted on fresh names.

Stored daily closes start at each holding’s first buy and stay daily for the whole holding life (a decade of quotes is a few megabytes, so the file is not pruned and the NAV chart is not stepped). The dashboard only reads quotes for the selected chip (plus the latest close per name).

New mark-to-market buys and watchlist adds are checked against the feed first. Deposit, CT, and unlisted PPR are valued locally and are not probed.

USD quotes stay USD and convert with stored EUR-per-USD even when the instrument is booked in EUR.

## Broker CSV

From Overview, import Trading 212 History, DEGIRO Transactions + Account statement, or Revolut Stocks/Invest account statement. You can import again later; duplicate rows are skipped. Export CSV, not Excel. DEGIRO files work in English, Dutch, Portuguese, Spanish, French, German, and Italian. Revolut statements can use English, Spanish, or Portuguese headers.

Lots always persist. You can edit quote symbols on the review screen if a ticker has no live close; those names still import, with a warning. Cash interest on uninvested balances is booked separately from deposits (not extra money you put in). A commodity row with no EUR amount, and a non-zero fee with no FX rate, are skipped instead of booked at zero.

Settings can turn on Trading 212 read-only sync. It uses HTTP Basic (API key and secret), follows history cursors, and does not place orders. Sync runs after unlock. A second sync does not double-book the same broker reference.

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
- Screenshots of the ledger and PIN are blocked (`FLAG_SECURE`). While a system file picker is open the flag is cleared and an opaque cover hides ledger pixels.
- No cloud backup of the database (`allowBackup=false`).
- Network is HTTPS GET only to Frankfurter, Alpha Vantage, CoinGecko, and Stooq (`stooq.pl`, with `stooq.com` still allowed if the host redirects). Trading 212 sync, when enabled, is HTTPS GET to `live.trading212.com` only.

## Build from source

JDK 17+, Android SDK (compile 37 / target 37). `local.properties` is gitignored; set `sdk.dir` or `ANDROID_HOME`. The app version is 0.4.0 (versionCode 4). Do not move the 0.2.0 or 0.3.0 tags.

```bash
./gradlew :app:assembleDebug
./gradlew :domain:test :domain:koverVerify :app:testDebugUnitTest
bash scripts/ci-local.sh
```

Contributor process, analog pins, and the authored backlog live in [`docs/ai-agent-handoff.md`](docs/ai-agent-handoff.md), [`docs/issues.yml`](docs/issues.yml), and [`docs/limitations.md`](docs/limitations.md).
