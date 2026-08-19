# Scanner exceptions and scope filters

Finding suppressions (`mobsf-ignore`, `nosemgrep`, CodeQL `// lgtm`, OSV ignore lists) are not a
fix. Prefer changing the code. If a check is skipped, the skip lives here with **why the code
cannot be the fix**.

There are currently **no finding suppressions** in application source. Remaining rows are
**scope filters** (what the tool is asked to look at), not “this finding is fine”.

## Finding suppressions (must stay empty unless a row is added)

| Tool | ID / annotation | Location | Why this is not a code fix | Review |
| --- | --- | --- | --- | --- |
| — | — | — | No `mobsf-ignore`, `.semgrepignore` findings, `osv-scanner.toml` ignores, or `.gitleaks.toml` allowlists. | 2026-08-18 |

Room `execSQL` on static DDL used to trip MobSF `android_kotlin_sql_raw_query` (the rule is
`$D.execSQL(...)`, not taint). That was **not SQL injection**: the four statements were
compile-time `ALTER`/`CREATE` with no user input. The code fix is Room `AutoMigration` so
generated SQL is outside `app/src/main` (mobsfscan paths). Do not put `mobsf-ignore` back.

## Scope filters (not finding ignores)

These exclude paths or classpaths the tools should not treat as product code, or set the
CI fail bar (CI-005: High/Critical). They do not mute a known vuln in FinSilo source.

| Tool | Filter | Why this is scope, not a fix | Alternative if we “solved it in code” |
| --- | --- | --- | --- |
| semgrep | `--exclude .github/scaffold --exclude docs/guardrails` | Analog pins are other repos’ samples, not FinSilo. | Stop vendoring analog docs (not planned). |
| semgrep | `--severity ERROR` | CI-005 / KT-SEC-002: warning-level community rules are noisy; ERROR fails the job. | `semgrep login` + org pack (LIM-SG). |
| CodeQL | `paths-ignore`: analog dirs, `**/build/**`, `**/generated/**` | Same analog pin; generated/KSP SQL is not authored. | N/A |
| CodeQL / MobSF / OSV | `fail-on-sarif-severity.py --min-severity high` | CI-005: High/Critical fail; medium is printed. | Lower the fail bar only with a recorded deviation. |
| MobSF mobsfscan | Scan `app/src/main` and `domain/src/main` only | Tests, build, analog pins, and KSP output are not the app. Full MobSF APK Docker is not in CI. | Run the MobSF container in a Docker-capable job. |
| OSV Scanner | CycloneDX `includeConfigs` runtime classpaths; `skipConfigs: (?i).*test.*` | Test-only libs (Robolectric, etc.) are not shipped. First unfiltered BOM flagged Netty/Jackson/BouncyCastle from the test graph. | Ship those libraries (we do not). |
| OSV Scanner | Gradle BOM instead of APK Syft | Dex APK has almost no Maven coordinates; OSV needs the resolved Gradle graph. | N/A; Syft still uploads an APK SBOM (LIM-SBOM). |

## Agent rule

Do not add a suppression, ignore file, or extra `paths-ignore` to make a scanner green.
Fix the code, or add a row to the tables above with a review date. Never lower Kover 95/95
or CI-005 to clear a finding.
