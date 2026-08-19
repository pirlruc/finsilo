# Limitations

Authored leftovers that already have an Epic/Task stay in [`docs/issues.yml`](issues.yml).
This file is only for **known limits that are not issues** (product/API/CI facts).

| ID | Limitation | How to overcome | Status | Stops being a limitation when |
| --- | --- | --- | --- | --- |
| LIM-SG | Semgrep CI uses community `r/kotlin` + `r/generic.secrets` without login. CodeQL `security-extended` and MobSF mobsfscan now add a second/third SAST layer; they do not replace a vendored/login semgrep pack. | `semgrep login` (or a vendored rulepack); keep `--severity ERROR --error`. | **Open — SAST depth (semgrep registry)** | Registry packs run in `security.yml` |
| LIM-REL | No cosign signing or SLSA provenance (SC-SIGN-001, SC-PROV-001). No GitHub Release yet. | Tag/release workflow with keyless cosign and `actions/attest-build-provenance`; attach the CI CycloneDX SBOM. | **Open — no published artifact** | A signed, attested GitHub Release is produced from CI |
| LIM-EMU | CI runs **Robolectric** Room tests, not an emulator. SQLCipher native is not exercised. | Instrumented emulator/device job. | **Open — device CI** | A failing SQLCipher/Compose test fails that job |
| LIM-AV | Alpha Vantage free tier is about 25 calls/day. | Paid AV tier, fewer US names, or another GET-only US history source that is not unofficial Yahoo. | **Open — API quota** | Quota no longer blocks a normal US book, without unofficial Yahoo |
| LIM-GH | GitHub issue publish 403s: token is `issues=read`. | PAT/App with **Issues: Read and write**; then scaffold + `issues-sync.sh`. | **Open — token scope** | Labels, milestones, and issues sync from `docs/issues.yml` without 403 |

Closed this pass (kept here so the “stops when” is visible):

| ID | Limitation | Status | Stops being a limitation when (met) |
| --- | --- | --- | --- |
| LIM-COV | `:domain` Kover branch below 95 | **Closed** | `./gradlew :domain:koverVerify` green at profile 95/95 (branch ~95.1%, line ~99.7%) |
| LIM-SUB | `GITHUB_TOKEN` cannot clone private analog | **Closed** | Required jobs run `scripts/ci-init-guardrails.sh` with `GUARDRAILS_READ_TOKEN` and init **only** `docs/guardrails`. `.github/scaffold` is not cloned in CI (templates are synced). Dependabot/forks without the secret still read the consumer copy. |
| LIM-MI | No Kotlin MI tool | **Closed** | `scripts/check-maintainability.py` runs multimetric SEI on `:domain` vs `min_maintainability_index` |
| LIM-MI-UI | Compose file MI below 40 | **Closed** | Dashboard/ledger screens split; `scripts/check-maintainability.py` scans `:app` as well as `:domain`; min SEI ≥ 40 |
| LIM-HOOK | gitleaks CI-only | **Closed** | `.pre-commit-config.yaml` + Dependabot `pre-commit` ecosystem |
| LIM-MIN | Release minify off | **Closed** | `isMinifyEnabled` / R8 on; `:app:assembleRelease` in CI (debug signing) |
| LIM-EU | Stooq-first suffixes incomplete | **Closed** | `ListedQuoteRouting` covers `.L`/`.UK`/`.SW`/`.LS`/… with tests |
| LIM-NAV | Full NAV rebuild on every ledger/quote change | **Closed** | `RebuildNavHistoryUseCase` keeps prefix points and walks from `changedFrom` |
| LIM-SBOM | No CycloneDX | **Closed for CI** | `scripts/run-syft-sbom.sh` writes an APK CycloneDX JSON (few Maven coords in a dex APK); OSV Scanner gates the Gradle `cyclonedxBom` BOM instead. `android.yml` still uploads `finsilo-sbom`. Attaching to a GitHub Release is LIM-REL |

Do not copy these rows into `docs/issues.yml` unless they become planned work. Do not reopen [FS-DEC-001](issues.yml) for them.
FS-008 (typed JSON parsers when a third feed lands) stays in `issues.yml` only.
