# Architecture

FinSilo is an on-device portfolio tracker. The ledger does not leave the phone.

## Actors

| Actor | What it can do |
| --- | --- |
| Device owner | Unlocks with a PIN or biometrics, edits the ledger, imports a broker file, and writes an encrypted backup. |
| Compose UI | Shows the overview, holdings, activity, insights, and settings. It does not hold the database key after the session ends. |
| Domain | Books FIFO lots, values holdings in EUR, and parses broker files. It has no Android APIs and no network client. |
| Room and SQLCipher | Store the ledger. Each vault is its own database. The default file is `finsilo.db`. |
| Android Keystore | Wraps the database key and the preference secrets. The PIN and the recovery code are not stored as plaintext. |
| Quote and broker hosts | Optional HTTPS reads. They never receive the ledger. |

## External interfaces

The released app has no server of its own. The network security config allows HTTPS only, and only to these hosts:

- `api.frankfurter.app` and `api.frankfurter.dev` for FX
- `www.alphavantage.co` for an optional market-data key
- `api.coingecko.com` for crypto prices
- `stooq.com` and `stooq.pl` for listings and gold
- `live.trading212.com` for optional read-only history

Cleartext is off. Certificate checks use the system trust store. Trading 212 calls are GET, and the client refuses a URL that is not on its allow-list. The response body is capped.

The other inputs are files the user picks: broker CSV, an encrypted `.fsi` backup, and the tax CSV or PDF the user exports. A backup is restored only after the recovery code unwraps it, and only after the user confirms an overwrite.

## What a user can expect

- The ledger is encrypted at rest, and the screen is blocked from screenshots while the ledger or the PIN is showing.
- Android backup of the database is off.
- Quotes and broker history are optional. The app works with no account at a broker.
- The IRS preview and the FIRE notes are not tax advice and not a promised return.

What a user must not expect: a cloud copy of the ledger, a way to place an order, or a patch for an older tag. Only the latest GitHub Release is supported.

## Attack surface

The likely problems are a stolen or shared phone, PIN guessing, a copied backup file, a replaced APK, and a hostile quote or history response. There is no account on a FinSilo server, so there is no remote attacker against a FinSilo API.

The counters are the Keystore wrap, the SQLCipher database, the 15-minute wipe of the in-memory key, the host allow-list, the response cap, and the signed release.

## Dependencies

Direct libraries are pinned in `gradle/libs.versions.toml`. Dependabot opens update pull requests. Security updates are not delayed. Other updates wait for the monthly group. `dependency-review` and `supply-chain` must pass before a change reaches `main`. A release is not cut while either check is failing. An exception is a recorded suppression with a reason.

## Testing

A major behaviour change adds or updates the automated tests for that behaviour.

Locally, `./gradlew :domain:test :domain:koverVerify :app:testDebugUnitTest` runs the suites. On a pull request, `domain-tests.yml` runs the domain tests and the domain coverage gate, and `android.yml` runs the app unit tests. The domain gate is 95% statement and branch. The app gate is the FS-041 floor, 87% lines and 75% branches.
