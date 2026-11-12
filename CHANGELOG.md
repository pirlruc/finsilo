# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-11-12

- License the app as GPL-3.0-only and reserve EU text-and-data-mining rights in NOTICE.
- Value GBP, GBX, CHF, DKK, SEK, and NOK with Frankfurter crosses and a stored-rate fallback.
- Read CoinGecko prices with a typed JSON parser.
- Split the home screen into Overview, Holdings, Activity, and Insights.
- Call shared Android and common CI workflows. Release signing fails closed without a keystore.

## [Unreleased]

- Fix broker CSV import for DEGIRO transactions and account statements,
  Revolut commodity and crypto files, and Trading 212 USD buys (conversion
  fee, FX from total, IE `.DE` / DK `.CO` suffixes).
- Read CoinGecko closes from the `prices` array only.
- Round auto-funded buy notionals before the cash check, and value a TWR
  sub-period from the transaction prefix when stored NAV is end-of-day.
- Pin guardrails 1.8.0 and github-scaffold 1.7.0. Cite methodologies 1.7.0.
- Drop the Kotlin SEI maintainability-index gate (KT-CPLX-002). Match
  `lint_exception_max_days` and the Android lint error floor.
- Replace unchecked ViewModel factory casts with `viewModelFactory`.
