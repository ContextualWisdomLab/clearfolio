# KRW 2B Sale-Readiness Security Evidence

Date: 2026-07-02

## SAST

Command:

```bash
uvx semgrep --config p/java --metrics=off --error --json --output docs/qa/evidence/2026-07-02-krw2b-sale-readiness/semgrep.json src/main/java src/test/java
```

Result:

- Semgrep completed successfully.
- Rules run: 60 Java rules.
- Targets scanned: 31 tracked files.
- Findings: 0.
- Errors: 0.

Evidence:

- `semgrep.json`

## GitHub Checks

Repository workflows visible to this run:

- CodeQL: active.
- Dependency Graph: active.
- Copilot: active.

The pull request should use the normal GitHub check suite for hosted
code-scanning evidence after push.
