# Architecture Map

Last updated: 2026-08-05

## System Purpose

Clearfolio Viewer is an MVP backend that accepts document uploads, processes conversion asynchronously, exposes conversion status, and serves viewer bootstrap metadata when conversion succeeds.

Runtime stance: Spring WebFlux is adopted as the current non-blocking web runtime (Servlet/MVC path is not the selected implementation for this repo).

S2S chain (target integration path): `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`.
Current state: viewer/state API is implemented in this repository; downstream S2S orchestration remains planned and documented.

## Runtime Components

- `ConversionController` (`src/main/java/com/clearfolio/viewer/controller/ConversionController.java`)
  - `POST /api/v1/convert/jobs`: async submit contract.
  - `POST /api/v1/convert/jobs/{jobId}/retry`: operator retry for dead-lettered jobs.
  - `GET /api/v1/convert/jobs/{jobId}`: status polling.
  - `GET /api/v1/viewer/{docId}` (+ alias): viewer bootstrap JSON/state-gated responses.
- `ViewerUiController` (`src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`)
  - `GET /viewer/{docId}`: HTML viewer UI entrypoint (loading/failed/ready) that embeds PDF.js.
- `ArtifactController` (`src/main/java/com/clearfolio/viewer/controller/ArtifactController.java`)
  - `GET /artifacts/{docId}.pdf`: serves PDF bytes for SUCCEEDED jobs with basic HTTP Range support.
- `HealthController` (`src/main/java/com/clearfolio/viewer/controller/HealthController.java`)
  - `GET /healthz`: process liveness from Spring Boot `LivenessState`.
  - `GET /readyz`: traffic readiness from Spring Boot `ReadinessState`.
  - Probe payloads disclose only a controlled state label and use `Cache-Control: no-store`.
- `DefaultDocumentConversionService` (`src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`)
  - Validation, content hash generation, dedupe lookup, repository persistence, worker enqueue.
  - PDF passthrough: uploads that declare PDF (extension/content type) and carry the `%PDF-` magic header are seeded into the artifact store as-is, so the original bytes are served instead of a generated placeholder.
- `DefaultDocumentValidationService` (`src/main/java/com/clearfolio/viewer/service/DefaultDocumentValidationService.java`)
  - Enforces extension blocklist and size limits, including auditable policy-override exception lane.
- `DefaultConversionWorker` (`src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`)
  - Runs conversion on a bounded executor with retry scheduling and dead-letter fallback.
- `ArtifactStore` (`src/main/java/com/clearfolio/viewer/artifact/ArtifactStore.java`)
  - Stores converted PDF bytes by docId.
- `FileSystemArtifactStore` (`src/main/java/com/clearfolio/viewer/artifact/FileSystemArtifactStore.java`)
  - Default disk-backed artifact store; persists bytes plus minimal metadata under `clearfolio.artifact-store.root-dir` (default `data/artifacts`) so artifacts survive restarts, with an in-memory cache on the read path.
- `InMemoryArtifactStore` (`src/main/java/com/clearfolio/viewer/artifact/InMemoryArtifactStore.java`)
  - In-memory artifact store implementation used by tests and `clearfolio.artifact-store.mode=in-memory`.
- `ArtifactStoreConfig` (`src/main/java/com/clearfolio/viewer/config/ArtifactStoreConfig.java`)
  - Selects the artifact store implementation from `ArtifactStoreProperties`.
- `PdfBoxArtifactGenerator` (`src/main/java/com/clearfolio/viewer/artifact/PdfBoxArtifactGenerator.java`)
  - Generates a placeholder one-page PDF via PDFBox for non-PDF sources; real docx/hwp conversion remains future work.
- `InMemoryConversionJobRepository` (`src/main/java/com/clearfolio/viewer/repository/InMemoryConversionJobRepository.java`)
  - In-memory job store and content-hash dedupe index.
- `ConversionJob` (`src/main/java/com/clearfolio/viewer/model/ConversionJob.java`)
  - Domain lifecycle and retry metadata (`attemptCount`, `maxAttempts`, `retryAt`, `deadLettered`) plus manual dead-letter retry transition.
- `ViewerBootstrapResponse` (`src/main/java/com/clearfolio/viewer/api/ViewerBootstrapResponse.java`)
  - Includes deterministic `sourceExtension` and `rendererAdapter` metadata for viewer adapter bootstrap.

## Availability Model

- Liveness and readiness are separate operational contracts.
- Liveness determines restart eligibility and must not depend on shared external services.
- Readiness determines whether this instance receives traffic and may later include instance-local startup-recovery or overload signals through Spring availability events.
- The accepted ADR and Kubernetes example are in `docs/operations/2026-08-05-availability-probes.md`.

## State Model

- Status values: `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
- Retry-exhausted terminal state remains `FAILED` and is identified by `deadLettered=true`.

## Operational Gates

- Build and test gates are defined in `AGENTS.md` and include:
  - `mvn -B --no-transfer-progress verify` as the single complete merge-evidence command.
  - JaCoCo 100% production line and branch coverage for `com.clearfolio.viewer.*` within the `verify` lifecycle.
  - Warning-free public Javadoc validation within the same `verify` lifecycle.
  - Markdown lint for changed documentation.

Mandatory AC list (exact):

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

Optional tracks:

- client DB pooler
- PostgreSQL 17

## Detailed Design Docs

- `docs/architecture.md`
- `docs/prd-integrated-document-viewer-platform.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/submit-policy-adapter-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/diagrams/retry-deadletter-flow.md`
- `docs/operations/2026-08-05-availability-probes.md`
- `docs/engineering/acceptance-criteria.md`
- `docs/workflow/one-day-delivery-plan.md`
