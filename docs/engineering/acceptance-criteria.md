# Engineering Acceptance Criteria

Last updated: 2026-08-06

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

## Availability contract

- `GET /healthz` is process liveness, sourced from Spring Boot
  `LivenessState`. It returns `200 {"status":"ok"}` only while the process is
  `CORRECT`; `BROKEN` returns `503 {"status":"broken"}`.
- `GET /readyz` is traffic readiness, sourced from Spring Boot
  `ReadinessState`. It returns `200 {"status":"ready"}` only while the
  instance is `ACCEPTING_TRAFFIC`; otherwise it returns
  `503 {"status":"not_ready"}`.
- Both responses use `Cache-Control: no-store` and expose only controlled state
  labels.
- Liveness must not depend on a shared database, object store, gateway, model
  provider, or another external service. A shared-service outage is not by
  itself evidence that this process requires restart.
- Future readiness contributors must be instance-local routing conditions,
  publish Spring availability events, and include deterministic failure and
  recovery tests.
- The accepted decision, rollback rule, Kubernetes example, and authoritative
  references are recorded in
  `docs/operations/2026-08-05-availability-probes.md`.

## Delivery context chain

- `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`
- This repository owns the Clearfolio Viewer side of the contract and its state,
  authorization, document, artifact, availability, and operational gates.

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
| coverage | JaCoCo 0.8.15 applies bundle-level `LINE` and `BRANCH` `MISSEDCOUNT` limits with a maximum of `0` | `mvn -B --no-transfer-progress verify`; inspect `target/site/jacoco/jacoco.csv` and the exact-head CI job |
| docstring | Maven Javadoc Plugin 3.12.0 runs Java 21 doclint for public production APIs and fails on warnings or errors | `mvn -B --no-transfer-progress verify`; inspect `target/reports/apidocs` and the exact-head CI job |
| non-blocking web | Request paths do not execute document conversion inline; liveness and readiness remain separate non-blocking probes | `ConversionController`, `DefaultDocumentConversionService`, `HealthController`, and their concurrency/integration tests |
| lightweight queue | Capacity, rejection, retry, processing lease, and dead-letter behavior are executable contracts | `ConversionExecutorConfig`, `DefaultConversionWorker`, repository/state-store tests, and exact-head fuzzing |
| warning 0 | Java compilation uses `-Xlint:all -Werror`; Maven report acceptance rejects skipped and zero-test evidence | `mvn -B --no-transfer-progress verify`, `python3 scripts/verify_maven_test_reports.py`, and exact-head CI |
| deprecated 0 | Deprecated API warnings are build failures | `mvn -B --no-transfer-progress verify` |
| 1-day schedule+security verification | Required GitHub Checks must be successful for the exact current head; queued, pending, cancelled, stale-head, or skipped-required outcomes are not passing | Delivery-plan evidence plus GitHub CI, Security Scan, SAST Semgrep, fuzz, automated review, independent approval, and branch-protection evidence |

## Evidence boundaries

- Local output is diagnostic evidence only. Merge evidence must identify the
  exact commit SHA and protected GitHub workflow runs for that SHA.
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
- Availability decision: `docs/operations/2026-08-05-availability-probes.md`.

## References

Apache Software Foundation. (2026). *Apache Maven Javadoc Plugin 3.12.0:
`javadoc:javadoc`*. Retrieved August 5, 2026, from
https://maven.apache.org/plugins/maven-javadoc-plugin/javadoc-mojo.html

Apache Software Foundation. (2026). *Surefire reports*. Maven Surefire Plugin.
Retrieved August 6, 2026, from
https://maven.apache.org/surefire/maven-surefire-plugin/examples/reporting.html

Broadcom, Inc. (n.d.). *SpringApplication: Application availability (Spring Boot
3.5.16)*. Spring. Retrieved August 5, 2026, from
https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.application-availability

JaCoCo. (2026). *JaCoCo Maven plug-in: `jacoco:check`*. Retrieved August 5,
2026, from https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html

The Kubernetes Authors. (2026, April 17). *Liveness, readiness, and startup
probes*. Kubernetes.
https://kubernetes.io/docs/concepts/workloads/pods/probes/
