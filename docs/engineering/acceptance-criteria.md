# Engineering Acceptance Criteria

Last updated: 2026-08-09

This document is the canonical acceptance policy for the current Clearfolio
Viewer delivery baseline. Historical evidence snapshots remain useful for
provenance, but the required source of truth is the exact pull-request head and
its protected GitHub Checks.

## Mandatory AC list (exact)

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

The labels above are stable governance identifiers. Their executable meanings
are defined by the fail-closed gates below; changing a label requires an ADR and
a coordinated update to `AGENTS.md`, `CLAUDE.md`, and both architecture maps.

## Runtime stance

- Non-blocking web runtime is implemented with WebFlux
  (`spring-boot-starter-webflux`).
- Servlet/MVC runtime is not the selected implementation for this repository
  baseline.
- Document conversion remains asynchronous; HTTP request handlers submit work
  and expose status, retry, viewer, and artifact workflows rather than waiting
  for conversion completion.

## Delivery context chain

- `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`
- This repository owns the Clearfolio Viewer side of the contract and its state,
  authorization, document, artifact, and operational gates.

## Required local acceptance commands

```bash
mvn -B --no-transfer-progress verify
python3 scripts/verify_maven_test_reports.py
```

The commands are intentionally shared with CI. A contributor must not substitute
`mvn test`, skip the documentation execution, disable JaCoCo, lower a threshold,
suppress warnings, omit test-report verification, or present evidence containing
skipped or zero executed tests.

The report gate requires at least one Surefire `TEST-*.xml` report, a positive
total test count, zero skipped tests, zero failures, and zero errors. Every
`testsuite` element must explicitly provide non-negative integer `tests`,
`skipped`, `failures`, and `errors` attributes; an omitted outcome count is
incomplete evidence and fails closed rather than being inferred as zero. When
Failsafe `TEST-*.xml` reports are present, the same rules apply. Missing,
malformed, empty, negative-count, skipped, failing, or error-bearing report
evidence fails closed even when a preceding Maven process returned success.
Each XML report must be UTF-8, may include a UTF-8 byte-order mark, is limited to
16 MiB, and is rejected before parsing when it contains a NUL byte, DTD, or
entity declaration. This prevents alternate encodings from hiding
external-entity or expansion payloads from the pre-parse checks, even when test
code can write report files.

## Mandatory AC evidence mapping

| AC | Fail-closed gate | Reproduction and evidence |
| --- | --- | --- |
| coverage | JaCoCo 0.8.15 applies bundle-level `LINE` and `BRANCH` `MISSEDCOUNT` limits with a maximum of `0` | `mvn -B --no-transfer-progress verify`; inspect `target/site/jacoco/jacoco.csv` and the exact-head CI `Maven test` job |
| docstring | Maven Javadoc Plugin 3.12.0 runs Java 21 doclint for public production APIs and fails on warnings or errors | `mvn -B --no-transfer-progress verify`; inspect `target/reports/apidocs` and the exact-head CI `Maven test` job |
| non-blocking web | Request paths do not execute document conversion inline | `ConversionController`, `DefaultDocumentConversionService`, and their concurrency/integration tests |
| lightweight queue | Capacity, rejection, retry, processing lease, and dead-letter behavior are executable contracts | `ConversionExecutorConfig`, `DefaultConversionWorker`, repository/state-store tests, and exact-head fuzzing |
| warning 0 | Java compilation uses `-Xlint:all -Werror`; Maven report acceptance rejects skipped and zero-test evidence | `mvn -B --no-transfer-progress verify`, `python3 scripts/verify_maven_test_reports.py`, and the exact-head CI `Maven test` job |
| deprecated 0 | Deprecated API warnings are build failures | `mvn -B --no-transfer-progress verify` in the exact-head CI `Maven test` job |
| 1-day schedule+security verification | Required GitHub Checks must be successful for the exact current head; queued, pending, cancelled, stale-head, or skipped-required outcomes are not passing | Delivery-plan evidence plus the job-scoped exact-head evidence contract below |

## Exact-head GitHub evidence authority

The acceptance record is job-scoped. A green workflow name without the relevant
job identity and revision proof is insufficient.

- **CI / Maven test** — for pull requests, `actions/checkout` must use
  `${{ github.event.pull_request.head.sha }}` (or the workflow's equivalent
  exact-source expression), and `Verify exact checked-out revision` must prove
  `git rev-parse HEAD` equals that source head. The same job executes
  `mvn -B --no-transfer-progress verify` and then
  `python3 scripts/verify_maven_test_reports.py`. This is the authoritative
  source-head build, test, coverage, Javadoc, warning, deprecated-API and test-
  report evidence.
- **CI / Maven merge compatibility** — `actions/checkout` must use
  `${{ github.sha }}` for the pull-request synthetic merge revision and the job
  must prove `git rev-parse HEAD` equals that value before running Maven verify
  and test-report validation. Synthetic-merge success demonstrates integration
  compatibility only; it never substitutes for source-head evidence.
- **CI / Buyer-readiness script tests** — checkout and explicit revision proof
  must bind the script-policy tests to the exact source head before executing
  the repository's script-test suite.
- **Security Scan and SAST Semgrep** — the accepted workflow runs must be
  associated with the same exact source-head SHA being considered for merge.
  A successful run from a predecessor head, synthetic merge only, or another
  ref is stale evidence. Job/check conclusions must be complete and successful.
- **fuzz** — every configured matrix target is independent evidence. For the
  current workflow this means `ArtifactTokenParserFuzzTest`,
  `DocumentValidationFuzzTest`, and `TenantClaimsFuzzTest`; each target checks
  out and explicitly verifies the same source-head SHA. One successful matrix
  target cannot stand in for a missing, cancelled, skipped, or failed sibling.
- **automated review** — CodeRabbit, OpenCode/Noema, GHAS and other review or
  security evidence must identify or be demonstrably bound to the same source
  head. Comment/status-only evidence is not a counted independent approval.
- **independent approval** — the formal GitHub review submission must come from
  an eligible non-author reviewer under the live repository/ruleset policy and
  apply to the unchanged head. A predecessor-head approval, author review,
  model verdict, check status, or advisory comment does not count.
- **branch protection / ruleset** — evaluate the live required-check and review
  policy against the unchanged expected head immediately before merge. A
  historical PR `base.sha` is not the current protected base-ref tip.

The merge record therefore keeps `source_head_sha`, the PR's historical base
snapshot when useful for provenance, the independently resolved live base tip,
workflow/run identity, job identity, and review identity as separate evidence.
No single green badge collapses those authorities.

## Methodological rationale for evidence gates

The exact coverage threshold is a deliberate structural invariant, not a claim
that code coverage alone establishes test effectiveness. Inozemtseva and Holmes
(2014) found that, after controlling for test-suite size, coverage was not
strongly correlated with test-suite effectiveness. Clearfolio therefore keeps
100% owned production line/branch coverage as a fail-closed completeness floor
**and separately requires domain-valid security, lifecycle, concurrency,
fidelity, accessibility, crash/restart, migration/rollback, and release
assertions**. A change must not satisfy the policy by adding execution without a
meaningful behavioral oracle.

Likewise, a test process returning exit code zero is not sufficient evidence
when report generation, test discovery, skipping, or oracle quality can fail
independently. Barr et al. (2015) describe the software-testing oracle problem:
determining whether observed output is correct is itself a central testing
problem. Clearfolio's report verifier therefore checks that tests actually ran,
that outcome counters are explicit, and that no skipped/failing/error result is
silently promoted to success. These literature references explain the evidence
model; the executable Maven/JUnit/JaCoCo contracts remain the repository's
normative merge gates.

## Evidence boundaries

- Local output is diagnostic evidence only. Merge evidence must identify the
  exact commit SHA and protected GitHub workflow runs/jobs for that SHA.
- A successful earlier head does not validate a later head.
- Generated reports containing local paths or internal runtime details remain
  local unless an explicit privacy and disclosure review approves publication.
- Historical snapshots under `docs/qa/evidence/` must not be described as the
  current gate after code, dependencies, tests, or workflows change.

## Optional tracks

- client DB pooler;
- PostgreSQL 17.

## Database and queue operating policy for a future persistent phase

- Queue requests must not wait for completion in the request path; use status
  polling, callbacks, or an equivalent durable asynchronous contract.
- Keep database transactions short and exclude external network calls from
  transaction scope.
- Use bounded timeouts, bounded retries, and `SKIP LOCKED` for
  lock-contention-sensitive worker loops.
- Read routing may use a provided read-only endpoint; lock-sensitive or strongly
  consistent flows remain on the primary.
- Pooler detection is best-effort (`SHOW VERSION;` in a `pgbouncer` or `pgcat`
  management database); the fallback state is `unknown`.
- New database objects must use at least two descriptive words and snake_case by
  default.

## Architecture linkage

- Root architecture map: `ARCHITECTURE.md`.
- Detailed architecture: `docs/architecture.md`.

## References

Apache Software Foundation. (2026). *Apache Maven Javadoc Plugin 3.12.0:
`javadoc:javadoc`*. Retrieved August 5, 2026, from
https://maven.apache.org/plugins/maven-javadoc-plugin/javadoc-mojo.html

Apache Software Foundation. (2026). *Surefire reports*. Maven Surefire Plugin.
Retrieved August 6, 2026, from
https://maven.apache.org/surefire/maven-surefire-plugin/examples/reporting.html

Barr, E. T., Harman, M., McMinn, P., Shahbaz, M., & Yoo, S. (2015). The oracle
problem in software testing: A survey. *IEEE Transactions on Software
Engineering, 41*(5), 507–525. https://doi.org/10.1109/TSE.2014.2372785

Inozemtseva, L., & Holmes, R. (2014). Coverage is not strongly correlated with
test suite effectiveness. In *Proceedings of the 36th International Conference
on Software Engineering* (pp. 435–445). Association for Computing Machinery.
https://doi.org/10.1145/2568225.2568271

JaCoCo. (2026). *JaCoCo Maven plug-in: `jacoco:check`*. Retrieved August 5,
2026, from https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html
