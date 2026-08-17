# Improvements

The authored backlog is [`docs/issues.yml`](issues.yml)
([github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr)).
Do not keep a second issue list here.

| What | Where |
| --- | --- |
| Locked RFC product decisions | [FS-DEC-001](issues.yml) |
| RFC leftovers O1–O14 and second-pass bugs | [FS-001](issues.yml)…[FS-016](issues.yml), [TOOL-001](issues.yml) |
| Kotlin quality/coverage/security CI | [GATE-001](issues.yml) |
| Android lint, assemble, instrumented tests | [GATE-AND-001](issues.yml) |
| Pack map (Kotlin/Android vs C++ vs Python) | [GATE-002](issues.yml) |

RFC phases in milestone names: **1** foundation · **2** data entry · **3** market APIs · **4** calc engine · **5** notifications · **6** dashboard.

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
```

Creating GitHub issues needs **Issues: Read and write**. `issues=read` 403s. Until then this file’s links are YAML ids, not GitHub numbers.

Do not reopen [FS-DEC-001](issues.yml) unless the user explicitly changes those answers. Do not record a fake lowered-gate deviation; open GATE tasks instead.
