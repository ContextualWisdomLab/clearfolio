# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
this repository.

## Read AGENTS.md first

`AGENTS.md` at the repository root is the canonical agent operating guide. It
defines mandatory quality, security, supply-chain, diligence, and merge gates.
This file complements it with commands and architecture context. When the two
files differ, `AGENTS.md` wins.

Related canonical documents:

- `ARCHITECTURE.md` — root component and state map.
- `docs/architecture.md` — detailed runtime flows and boundaries.
- `docs/engineering/acceptance-criteria.md` — exact-head acceptance policy.
- `docs/operations/2026-08-05-availability-probes.md` — liveness/readiness ADR.
- `README.md` — API scope, tenant-header contract, and compatibility notes.

## Common commands

Toolchain: Java 21, Maven, Spring Boot 3.5.x, and Python 3 for `scripts/`.

```bash
# Canonical local acceptance command. It compiles with -Xlint:all -Werror,
# runs all tests, enforces zero missed JaCoCo lines/branches, and generates
# warning-free public Javadocs.
mvn -B --no-transfer-progress verify

# Focused test selection during the red-green loop.
mvn -B --no-transfer-progress test -Dtest=ConversionControllerTest
mvn -B --no-transfer-progress test -Dtest=ConversionControllerTest#methodName

# Buyer-readiness helper tests, matching the repository CI job.
python -m pytest -q scripts

# Run the service and inspect the two distinct availability signals.
mvn spring-boot:run
curl -i http://localhost:8080/healthz
curl -i http://localhost:8080/readyz
```

Do not present `mvn test`, a manually generated coverage report, or an earlier
head as complete merge evidence. The authoritative command is `mvn verify`, and
protected GitHub Checks must be successful for the exact current head.

The license-policy, third-party-attribution, buyer data-room manifest, buyer
readiness scorecard, and Figma payload drift checks are Python scripts under
`scripts/`. Their exact invocations and current evidence paths are listed in
`AGENTS.md`.

## GitHub workflow model

Repository workflows under `.github/workflows/` run CI, Security Scan, SAST
Semgrep, and fuzzing. Organization-central workflows may add review, coverage,
security, and merge-policy evidence. CodeQL uses GitHub default setup; do not add
a duplicate repository-local advanced CodeQL workflow while default setup is
enabled.

A queued, pending, cancelled, skipped-required, or stale-head run is not passing.
Do not bypass branch protection or counted independent approval. Automated
reviews are advisory unless GitHub recognizes the reviewer identity as having
the permission required by the protected-branch rule.

## What this repository is

Clearfolio Viewer (`com.clearfolio` / `clearfolio-viewer`) is the backend for an
integrated document-viewing platform. It provides non-blocking upload submission,
asynchronous conversion with retry and dead-letter behavior, status polling,
signed artifact access, and a same-application PDF.js viewer.

The runtime is Spring WebFlux. Logging is Log4j2; the default Logback starter is
excluded. Conversion jobs currently use an in-memory repository and process-local
lifecycle evidence. Generated artifacts use the filesystem store by default,
with an in-memory implementation available for tests and explicitly configured
local runs. A durable SQL job repository remains a planned production slice.

Entry point:
`src/main/java/com/clearfolio/viewer/ClearfolioViewerApplication.java`.

Configuration:

- `src/main/resources/application.yml` — queue, retry, upload, artifact-store,
  availability, viewer, tenant-claim, and secret-mount settings.
- `src/main/resources/application-buyer-demo.yml` — buyer sandbox profile.

## High-level architecture

All production code lives under `src/main/java/com/clearfolio/viewer/`.

- `controller/`
  - `ConversionController`: submit, status, retry, delete, viewer bootstrap, and
    downloadable PDF endpoints.
  - `ViewerUiController`: `GET /viewer/{docId}` HTML viewer shell.
  - `ArtifactController`: signed artifact reads with single-range support.
  - `AdminController`: tenant-scoped privileged job operations.
  - `AnalyticsController`: KPI snapshots and evidence exports.
  - `HealthController`: `GET /healthz` liveness and `GET /readyz` readiness,
    both sourced from Spring Boot `ApplicationAvailability` and marked
    `Cache-Control: no-store`.
  - `ApiExceptionHandler`: shared error shape with privacy-safe trace evidence.
- `service/`
  - `DefaultDocumentValidationService`: upload constraints, extension policy,
    and HMAC-verified exception lane.
  - `DefaultDocumentConversionService`: validation, tenant-scoped content-hash
    dedupe, persistence, PDF passthrough, and worker enqueue.
  - `DefaultConversionWorker`: bounded execution, retry backoff, dead-lettering,
    and startup recovery for due jobs and stale leases.
- `repository/`
  - `ConversionJobRepository`: read, dedupe, and recoverable-job boundary.
  - `ConversionJobStateStore`: lifecycle-transition boundary and event trail.
- `model/`
  - `ConversionJob`: `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, and `FAILED`.
    Retry-exhausted jobs remain `FAILED` with `deadLettered=true`.
- `artifact/`
  - `FileSystemArtifactStore`: default restart-surviving PDF store.
  - `InMemoryArtifactStore`: test and explicitly selected local store.
  - `PdfBoxArtifactGenerator`: placeholder PDF generation for non-PDF sources;
    deterministic real Office conversion remains a separate product slice.
  - `ArtifactLinkService` and ledgers: short-lived signed links, revocation, and
    read-audit evidence.
- `auth/`
  - Tenant and permission enforcement for protected JSON APIs. Gateway-signed
    header claims are supported; this is not a complete production OIDC/JWT
    implementation.
- `analytics/`
  - KPI snapshot counters and export evidence.
- `api/`, `config/`, and `exception/`
  - DTOs, configuration, WebFlux filters, and domain exceptions.

The request path must never perform conversion inline. Controllers validate and
submit bounded work, clients poll status, and the viewer retrieves a signed
artifact only after a successful terminal state.

## Availability semantics

- `/healthz` answers whether the process is irrecoverably broken and should be
  restarted. Never add shared database, object-store, gateway, or model-provider
  dependencies to liveness.
- `/readyz` answers whether this instance should receive traffic. Future
  instance-local readiness contributors must publish Spring availability events
  and include deterministic failure-and-recovery tests.
- Do not configure both Kubernetes probes against `/healthz`.

## Tests and gates

Tests mirror the production package tree under `src/test/java/`. Security-sensitive
parsers and filename paths also have Jazzer targets.

Key rules:

- `mvn verify` enforces zero missed production lines and branches using JaCoCo
  0.8.15.
- Maven Javadoc Plugin 3.12.0 runs during `verify` with doclint and fails on any
  public API documentation warning or error.
- The compiler uses `-Xlint:all -Werror`; warnings and deprecated production API
  usage fail the build.
- Every public production type and member requires useful Javadoc.
- Tests must exercise real behavior, including failure, security, concurrency,
  and recovery paths; coverage-only assertions must still represent a valid
  contract.
- Markdown lint applies to changed documentation.
- Dependency changes must update security, license, SBOM, attribution, and buyer
  diligence evidence together when affected.
- Generated evidence containing local paths, credentials, private runtime
  details, or customer data remains local until disclosure review approves it.
- Dated plans and decisions use `YYYY-MM-DD-...` filenames.
- `.jules/` contains accumulated lessons for validation, HMAC canonicalization,
  viewer security, and performance; inspect it before modifying those areas.
