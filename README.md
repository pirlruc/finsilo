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

JDK 17 or newer, and an Android SDK with `compileSdk` 37. `local.properties` is gitignored; set `sdk.dir` or `ANDROID_HOME`. Direct libraries are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). The app version is 0.6.0 (versionCode 6).

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :domain:test :domain:koverVerify :app:testDebugUnitTest
```

A signed release APK comes only from [GitHub Releases](https://github.com/pirlruc/finsilo/releases).

## Check a release

Download `app-release.apk` and `app-release.apk.sha256` from the same release. `apksigner` is in the Android SDK build-tools.

```bash
sha256sum -c app-release.apk.sha256
apksigner verify --print-certs app-release.apk
```

`sha256sum` checks that the APK matches the published checksum. `apksigner` checks the signature and prints the signer certificate. The SHA-256 fingerprint of that certificate is `27:1A:61:08:4C:C2:55:6C:74:AE:DA:C2:A1:3D:A3:B7:D9:90:96:75:9B:C1:B5:DF:71:0A:D6:36:94:41:53:E7`.

Only the latest release is supported, until the next release replaces it.

## For contributors

Open a [GitHub issue](https://github.com/pirlruc/finsilo/issues) for a bug or an idea. Propose a code change as a pull request. Each commit by a person needs a `Signed-off-by` trailer, as in the [Developer Certificate of Origin](https://developercertificate.org/). Code, comments, and docs are in English.

A major behaviour change adds or updates the automated tests for that behaviour. ktlint (`android_studio`) and detekt must pass.

- [Architecture](docs/architecture.md)
- [Maintainers](docs/MAINTAINERS.md)
- [Security](SECURITY.md)
- [Decision log](https://github.com/pirlruc/finsilo/issues?q=label%3Aepic)
