# Limitations

Authored leftovers that already have an Epic/Task stay in [`docs/issues.yml`](issues.yml).
This file is only for **known limits that are not issues** (product/API/CI facts), plus
the one Phase 7 gate that is wired but not yet green.

| ID | Limitation | How to overcome | Status | Stops being a limitation when |
| --- | --- | --- | --- | --- |
| LIM-COV | `:domain` Kover **line is ≥95%** (statement rule ~98.8% green) but **branch is still ~81%** vs 95. `koverVerify` fails closed on branch coverage. Tracked as open [GATE-001-T3](issues.yml). | Add domain tests for remaining branches. Do not lower the profile or add a fake deviation. | **Open — line green, branch red** | `./gradlew :domain:koverVerify` is green at the profile 95/95 numbers |
| LIM-SUB | `docs/guardrails` and `.github/scaffold` are **private** submodules. GitHub Actions cannot clone them with `GITHUB_TOKEN`. CI reads [`config/kotlin.profile.thresholds.yml`](../config/kotlin.profile.thresholds.yml). | Public analog repos, or an Actions PAT + `submodules: recursive`. | **Open — analog clone** | Jobs read `docs/guardrails/kotlin/profile.thresholds.yml` directly |
| LIM-SG | Semgrep CI uses community `r/kotlin` + `r/generic.secrets` without login. | `semgrep login` (or a vendored rulepack); keep `--severity ERROR --error`. | **Open — SAST depth** | Registry packs run in `security.yml` |
| LIM-REL | No cosign signing or SLSA provenance (SC-SIGN-001, SC-PROV-001). No GitHub Release yet. | Tag/release workflow with keyless cosign and `actions/attest-build-provenance`; attach the CI CycloneDX SBOM. | **Open — no published artifact** | A signed, attested GitHub Release is produced from CI |
| LIM-EMU | CI runs **Robolectric** Room tests, not an emulator. SQLCipher native is not exercised. | Instrumented emulator/device job. | **Open — device CI** | A failing SQLCipher/Compose test fails that job |
| LIM-AV | Alpha Vantage free tier is about 25 calls/day. | Paid AV tier, fewer US names, or another GET-only US history source that is not unofficial Yahoo. | **Open — API quota** | Quota no longer blocks a normal US book, without unofficial Yahoo |
| LIM-GH | GitHub issue publish 403s: token is `issues=read`. | PAT/App with **Issues: Read and write**; then scaffold + `issues-sync.sh`. | **Open — token scope** | Labels, milestones, and issues sync from `docs/issues.yml` without 403 |

Closed this pass (kept here so the “stops when” is visible):

| ID | Limitation | Status | Stops being a limitation when (met) |
| --- | --- | --- | --- |
| LIM-MI | No Kotlin MI tool | **Closed** | `scripts/check-maintainability.py` runs multimetric SEI on `:domain` vs `min_maintainability_index` |
| LIM-MI-UI | Compose file MI below 40 | **Closed** | Dashboard/ledger screens split; `scripts/check-maintainability.py` scans `:app` as well as `:domain`; min SEI ≥ 40 |
| LIM-HOOK | gitleaks CI-only | **Closed** | `.pre-commit-config.yaml` + Dependabot `pre-commit` ecosystem |
| LIM-MIN | Release minify off | **Closed** | `isMinifyEnabled` / R8 on; `:app:assembleRelease` in CI (debug signing) |
| LIM-EU | Stooq-first suffixes incomplete | **Closed** | `ListedQuoteRouting` covers `.L`/`.UK`/`.SW`/`.LS`/… with tests |
| LIM-NAV | Full NAV rebuild on every ledger/quote change | **Closed** | `RebuildNavHistoryUseCase` keeps prefix points and walks from `changedFrom` |
| LIM-SBOM | No CycloneDX | **Closed for CI** | `scripts/run-syft-sbom.sh` writes CycloneDX JSON; `android.yml` uploads `finsilo-sbom`. Attaching to a GitHub Release is LIM-REL |

Do not copy these rows into `docs/issues.yml` unless they become planned work. Do not reopen [FS-DEC-001](issues.yml) for them.
FS-008 (typed JSON parsers when a third feed lands) stays in `issues.yml` only.
