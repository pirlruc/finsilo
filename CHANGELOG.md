# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Pin guardrails 1.8.0 and github-scaffold 1.7.0. Cite methodologies 1.7.0.
- Drop the Kotlin SEI maintainability-index gate (KT-CPLX-002). Match
  `lint_exception_max_days` and the Android lint error floor.
- Replace unchecked ViewModel factory casts with `viewModelFactory`.
