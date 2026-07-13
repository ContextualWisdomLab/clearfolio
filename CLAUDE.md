# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read AGENTS.md first

`AGENTS.md` at the repo root is the canonical agent operating guide. It defines the
mandatory quality and security merge gates (compile with zero warnings, tests,
100% JaCoCo coverage, JavaDoc, markdown lint, license/attribution/diligence drift
checks) and the change-management rule that any new gate must be added to
`AGENTS.md` in the same PR. Follow those gates before claiming any change complete.
This file complements `AGENTS.md` with commands and architecture context; when in
doubt, `AGENTS.md` wins.

Related canonical docs:

- `ARCHITECTURE.md` — root architecture map (components, state model, gate list).
- `docs/architecture.md` — detailed runtime flows and component boundaries.
- `docs/engineering/acceptance-criteria.md` — canonical acceptance policy with exact repro commands and evidence pointers.
- `README.md` — API scope, tenant-header contract, and compatibility notes.

## Common commands

Toolchain: Java 21, Maven (Spring Boot parent 3.5.x). Python 3 for `scripts/`.

```bash
# Compile (gate: warning/deprecated budget = 0; -Xlint:all -Werror is enforced)
mvn -DskipTests compile

# Full test suite (gate)
mvn test

# Single test class / single test method (standard Surefire selection)
mvn test -Dtest=ConversionControllerTest
mvn test -Dtest=ConversionControllerTest#methodName

# Coverage gate: JaCoCo line/branch missed must be 0 for com.clearfolio.viewer.*
mvn -q -Djacoco.includes=com.clearfolio.viewer.* \
  org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test \
  org.jacoco:jacoco-maven-plugin:0.8.13:report
# Report lands in target/site/jacoco/jacoco.csv

# JavaDoc gate (must produce no warnings/errors)
mvn -q -DskipTests javadoc:javadoc

# Run the app locally, then probe readiness
mvn spring-boot:run
curl -sS http://localhost:8080/healthz

# Python helper-script unit tests (unittest, no pytest dependency)
python3 -m unittest discover -s scripts
```

The license-policy, third-party-attribution, buyer data-room manifest, buyer
readiness scorecard, and Figma deck payload drift checks are Python scripts under
`scripts/`; the exact invocations (with the current evidence/policy file paths)
are listed in `AGENTS.md` and must pass for doc/dependency changes that touch them.

There are no repo-local CI workflows in `.github/workflows`; gates are reproduced
locally and evidence is committed under `docs/qa/evidence/`. CodeQL runs through
GitHub default setup (do not add a repo-local advanced CodeQL workflow), and
Dependabot config lives in `.github/dependabot.yml`.

## What this repository is

Clearfolio Viewer (`com.clearfolio` / `clearfolio-viewer`) is the MVP backend for
an integrated document viewer platform: non-blocking upload submit, async
conversion with retry/dead-letter, job status polling, and a PDF.js viewer served
from the same app. The runtime is Spring WebFlux (Servlet/MVC is explicitly not
the selected stack), logging is Log4j2 (the default Logback starter is excluded),
and the default job/artifact stores are in-memory (a SQL repository profile is
planned but not implemented).

Entry point: `src/main/java/com/clearfolio/viewer/ClearfolioViewerApplication.java`.
Configuration: `src/main/resources/application.yml` (`conversion.*` queue/retry/
upload limits, `viewer.security.frame-ancestors`) plus the `buyer-demo` Spring
profile (`application-buyer-demo.yml`) for buyer sandbox deployments.

## High-level architecture

All production code lives in `src/main/java/com/clearfolio/viewer/`:

- `controller/` — HTTP endpoints and exception mapping. `ConversionController`
  (submit `POST /api/v1/convert/jobs`, status polling, operator retry, viewer
  bootstrap JSON), `ViewerUiController` (`GET /viewer/{docId}` HTML shell),
  `ArtifactController` (`GET /artifacts/{docId}.pdf` with token verification and
  single-range support), `AdminController`, `AnalyticsController` (KPI snapshot),
  `HealthController` (`/healthz`), `ApiExceptionHandler` (shared error shape:
  `errorCode`, optional `code`, `message`, `traceId`, `details`).
- `service/` — `DefaultDocumentValidationService` (extension blocklist — HWP/HWPX
  blocked by default — size limits, HMAC-verified policy-override lane),
  `DefaultDocumentConversionService` (validation, content-hash dedupe, persist,
  enqueue), `DefaultConversionWorker` (bounded executor, retry backoff,
  dead-letter fallback, startup recovery sweep for stale leases).
- `repository/` — `ConversionJobRepository` (read/dedupe boundary, in-memory
  implementation) and `ConversionJobStateStore` (explicit lifecycle-transition
  boundary with a process-local event trail; designed as the seam for the future
  SQL implementation).
- `model/` — `ConversionJob` lifecycle (`SUBMITTED`, `PROCESSING`, `SUCCEEDED`,
  `FAILED`; retry-exhausted jobs stay `FAILED` with `deadLettered=true`).
- `artifact/` — `ArtifactStore` (in-memory PDF bytes), `PdfBoxArtifactGenerator`
  (PDFBox conversion stub), `ArtifactLinkService`/`ArtifactLinkLedger`
  (short-lived signed artifact tokens, revocation, read-audit ledger).
- `auth/` — tenant enforcement scaffold: protected JSON APIs require
  `X-Clearfolio-Tenant-Id`, `X-Clearfolio-Subject-Id`, `X-Clearfolio-Permissions`
  headers (optionally gateway-HMAC-signed); cross-tenant jobs are hidden as 404.
  This is not production OIDC/JWT validation.
- `analytics/` — KPI snapshot counters and export ledger.
- `api/` — response/request DTOs; `config/` — `ConversionProperties`, executor,
  security-headers WebFilter; `exception/` — domain exceptions.

Request flow: controller validates and enqueues (returns `202` fast; the request
path must never run conversion inline), the worker converts and stores the PDF,
clients poll status, then fetch viewer bootstrap JSON and the artifact via a
signed link. Static viewer assets (PDF.js shell, demo fixtures) live under
`src/main/resources/static/assets/viewer/`.

Tests mirror the package tree under `src/test/java/`, including
`config/DependencyPolicyTest` (blocks reintroducing `tika-parsers-standard-package`,
the default Logback starter, or the excluded Jakarta annotation dependency) and
the Jazzer fuzz target `controller/FuzzDownloadFilename` (ClusterFuzzLite marker
in `.clusterfuzzlite/`).

## Key conventions

- Coverage is absolute: JaCoCo line/branch missed must stay 0 for
  `com.clearfolio.viewer.*`, so every production change ships with tests covering
  all new lines and branches.
- Every public type/member needs JavaDoc; the JavaDoc gate fails on any warning.
- The compiler runs with `-Xlint:all -Werror` (`showWarnings`/`showDeprecation`
  on), so any warning or deprecated usage breaks the build.
- `checkstyle-suppressions.xml` at the repo root relaxes strict style checks
  (Javadoc, magic numbers, line length, etc.) for `src/test/java` only; production
  sources get no suppressions.
- Markdown lint applies to changed docs; rule overrides are in
  `.markdownlint.yaml`.
- Dependency changes are policy-sensitive: security pins and exclusions in
  `pom.xml` carry CVE-annotated comments, `osv-scanner.toml` holds narrowly
  scoped, time-boxed ignores, and license/SBOM/attribution evidence under `docs/`
  must be updated together (see `AGENTS.md` and `DependencyPolicyTest`).
- Gate evidence is committed under `docs/qa/evidence/`; plans and design docs
  under `docs/` use dated filenames (`YYYY-MM-DD-...`).
- `.jules/` holds accumulated lessons (performance, XSS, HMAC canonicalization);
  worth scanning before touching validation, viewer JS, or token signing code.
