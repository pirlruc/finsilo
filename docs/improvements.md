# Improvements

The authored backlog is [`docs/issues.yml`](issues.yml)
([github-issue-adr](https://github.com/pirlruc/methodologies/tree/1.2.0/github-issue-adr)).
Do not keep a second issue list here.

| What | Where |
| --- | --- |
| Locked RFC product decisions | [FS-DEC-001](issues.yml) |
| Product leftovers still open | [FS-008](issues.yml) (typed JSON parsers); value backlog [FS-017](issues.yml)–[FS-024](issues.yml), [FS-026](issues.yml) (Trading 212 API). CSV import [FS-025](issues.yml) is done. |
| Phase 7 still open | [GATE-001-T3](issues.yml) — Kover branch 95% (line already green; [limitations.md](limitations.md) LIM-COV) |
| Known limits not in the backlog | [docs/limitations.md](limitations.md) (open + closed-this-pass tables) |
| Pack map (Kotlin/Android vs C++ vs Python) | [GATE-002](issues.yml) |

RFC phases in milestone names: **1** foundation · **2** data entry · **3** market APIs · **4** calc engine · **5** notifications · **6** dashboard · **7** guardrails (last).

```bash
bash scripts/setup-issue-scaffold.sh
bash scripts/issues-sync.sh --repo pirlruc/finsilo --yaml docs/issues.yml --dry-run
```

Creating GitHub issues needs **Issues: Read and write**. `issues=read` 403s. Until then this file’s links are YAML ids, not GitHub numbers.

Do not reopen [FS-DEC-001](issues.yml) unless the user explicitly changes those answers. Do not record a fake lowered-gate deviation; open GATE tasks instead.
