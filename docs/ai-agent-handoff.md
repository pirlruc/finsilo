# AI Agent Handoff — finsilo

## Identity

| Field | Value |
| --- | --- |
| **App** | FinSilo |
| **Package** | `com.pirlruc.finsilo` |
| **Type** | Native Android (Kotlin, Compose), offline-first portfolio tracker |
| **Docs** | `docs/ai-agent-handoff.md`, `docs/improvements.md` |

## Current slice

Phase 6 dashboard + the Room/SQLCipher foundation needed to feed it.

| Module | Path | Notes |
| --- | --- | --- |
| domain | `domain/` | JVM. `PortfolioValuator`, history, allocation, market signals. No Android APIs. |
| app | `app/` | Compose dashboard, Vico charts, encrypted Room, sample seeder. |

## How to run checks

```bash
./gradlew :domain:test
./gradlew :app:assembleDebug
```

## Intentionally not in this tree

- Phase 2 Compose forms for Buy/Sell/Deposit.
- Phase 3 Alpha Vantage / Yahoo / CoinGecko WorkManager sync.
- Phase 4 YOC and TWR use cases (cost basis math exists; YOC/TWR do not).
- Phase 5 NotificationManager worker (signal detection **does** exist and is shown on the dashboard).

## Security

- SQLCipher passphrase in EncryptedSharedPreferences / Android Keystore.
- `android:allowBackup="false"` and backup exclusion rules.
- No INTERNET permission until a GET-only sync design lands.

## Sample data

`SamplePortfolioFactory` is deterministic synthetic data (not market data). Loaded only from the empty-state button.

*Last updated: 2026-08-16*
