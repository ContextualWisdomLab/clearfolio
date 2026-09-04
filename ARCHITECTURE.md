# Architecture Map

Last updated: 2026-09-03

## Document authority

- `ARCHITECTURE.md` is the repository-level component/context map and dependency-direction source.
- `docs/architecture.md` contains detailed runtime-flow and deployment/persistence notes.
- `docs/product-technical-gap-baseline.md` owns the code-current DDD/gap/remediation/evidence baseline.
- PR bodies record candidate-head evidence only and must not silently redefine the product architecture.

Keeping these roles explicit prevents architecture facts, release claims, and remediation state from becoming competing sources of truth.

## System Purpose

Clearfolio Viewer accepts document uploads, processes conversion asynchronously, exposes conversion status and viewer bootstrap metadata, serves PDF artifacts, and provides permission-gated tenant administration for recovery operations.

Runtime stance: Spring WebFlux is adopted as the current non-blocking web runtime (Servlet/MVC is not the selected implementation for this repository).

S2S chain (target integration path): `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`.
Current state: viewer/state API is implemented in this repository; downstream S2S orchestration remains planned and documented.

## Bounded contexts and dependency direction

The modular application currently separates these responsibilities:

- **Document Conversion & Viewing (core):** `ConversionJob` lifecycle, conversion submission, readiness/bootstrap, and artifact availability.
- **Conversion Execution & Recovery (supporting):** worker claim/retry/dead-letter/recovery behavior through `ConversionJobStateStore` and `ConversionWorker`.
- **Artifact Delivery (supporting):** artifact persistence and range-capable PDF delivery through `ArtifactStore`.
- **Tenant Administration (supporting):** permission-gated tenant-scoped list/delete/retry use cases through `DocumentConversionService`.
- **Access & Audit Boundary (generic):** request `TenantContext`/permission verification, credential resolution, and privacy-safe retry operator identity. Audit pseudonyms are correlation metadata, not authorization authority.

Dependency direction for tenant administration and its security boundary is:

```text
AdminController
  -> TenantAccessService
       -> CredentialRegistryPort
  -> DocumentConversionService (tenant-scoped application port)
       -> ConversionJobRepository
       -> ConversionJobStateStore
       -> ConversionWorker
       -> ArtifactStore
  -> RetryOperatorIdentityPort
       -> HmacRetryOperatorIdentityAdapter
            -> CredentialRegistryPort

BootstrapCredentialRegistryAdapter
  -> CredentialRegistryPort
```

The HTTP adapter must not fetch global/unscoped conversion aggregates and reconstruct tenant ownership locally. Missing and cross-tenant resources collapse to the same not-found outcome at the tenant-scoped application boundary. Cryptographic audit identity generation is isolated behind a port so controller/domain code does not own key material or equate audit correlation with access authority.

`BootstrapCredentialRegistryAdapter` is the current credential adapter for the tenant-claim and audit-pseudonym keys. Deployment values are copied into an immutable keyed registry during Spring bootstrap; `TenantAccessService` and `HmacRetryOperatorIdentityAdapter` resolve them only through `CredentialRegistryPort`. Missing tenant-claim key material fails closed with `503`; missing audit-correlation material yields a non-correlatable `unavailable:<version>` marker. The older artifact-token secret path is a separate known migration gap and must not be treated as precedent for new secret consumers.

## Runtime Components

- `ConversionController` (`src/main/java/com/clearfolio/viewer/controller/ConversionController.java`)
  - `POST /api/v1/convert/jobs`: async submit contract.
  - `POST /api/v1/convert/jobs/{jobId}/retry`: existing conversion recovery surface.
  - `GET /api/v1/convert/jobs/{jobId}`: status polling.
  - `GET /api/v1/viewer/{docId}` (+ alias): viewer bootstrap JSON/state-gated responses.
- `AdminController` (`src/main/java/com/clearfolio/viewer/controller/AdminController.java`)
  - Tenant-authorized administrative list/delete/retry endpoints.
  - Delegates aggregate ownership to tenant-scoped `DocumentConversionService` operations.
  - Delegates retry audit correlation to `RetryOperatorIdentityPort`.
- `TenantAccessService` (`src/main/java/com/clearfolio/viewer/auth/TenantAccessService.java`)
  - Verifies signed tenant claims and permission requirements before protected request behavior.
  - Resolves Spring-runtime tenant-claim HMAC material through `CredentialRegistryPort`; an unavailable credential fails closed.
  - Cross-tenant resource existence is intentionally hidden from external callers.
- `CredentialRegistryPort` and `BootstrapCredentialRegistryAdapter` (`src/main/java/com/clearfolio/viewer/security/`)
  - Provide the keyed runtime secret-resolution boundary for tenant-claim and audit-pseudonym consumers.
  - The bootstrap adapter copies provisioned transport values into immutable registry state once; runtime consumers do not bind those deployment values directly.
- `RetryOperatorIdentityPort` and `HmacRetryOperatorIdentityAdapter` (`src/main/java/com/clearfolio/viewer/security/`)
  - Produce versioned, keyed, purpose-separated retry audit pseudonyms from the dedicated audit pseudonym key resolved through the credential registry.
  - Missing key material yields a non-correlatable unavailable marker rather than plaintext or an unkeyed subject digest.
- `ViewerUiController` (`src/main/java/com/clearfolio/viewer/controller/ViewerUiController.java`)
  - `GET /viewer/{docId}`: HTML viewer UI entrypoint (loading/failed/ready) that embeds PDF.js.
- `ArtifactController` (`src/main/java/com/clearfolio/viewer/controller/ArtifactController.java`)
  - `GET /artifacts/{docId}.pdf`: serves PDF bytes for SUCCEEDED jobs with basic HTTP Range support.
- `DefaultDocumentConversionService` (`src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`)
  - Validation, content-hash generation, dedupe lookup, repository persistence, worker enqueue.
  - Tenant-scoped list/delete/retry implementations resolve ownership through the repository before returning or mutating aggregates.
  - PDF passthrough: uploads that declare PDF (extension/content type) and carry the `%PDF-` magic header are seeded into the artifact store as-is.
- `ConversionJobRepository` (`src/main/java/com/clearfolio/viewer/repository/ConversionJobRepository.java`)
  - Aggregate persistence port with tenant-scoped lookup/list compatibility contracts. Durable adapters should push those predicates into native storage queries.
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
  - Aggregate root for lifecycle/retry metadata (`attemptCount`, `maxAttempts`, `retryAt`, `deadLettered`) and manual dead-letter retry transition.
- `ViewerBootstrapResponse` (`src/main/java/com/clearfolio/viewer/api/ViewerBootstrapResponse.java`)
  - Includes deterministic `sourceExtension` and `rendererAdapter` metadata for viewer adapter bootstrap.

## State Model

- Status values: `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
- Retry-exhausted terminal state remains `FAILED` and is identified by `deadLettered=true`.
- Tenant ownership is an invariant for externally addressable administrative query/mutation operations and is not a presentation filter.

## Operational Gates

Build and test gates are defined in `AGENTS.md` and include:

- `mvn -DskipTests compile`
- `mvn test`
- JaCoCo line/branch 100% for `com.clearfolio.viewer.*`
- JavaDoc gate: `mvn -q -DskipTests javadoc:javadoc`
- Markdown lint for changed docs

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
- `docs/product-technical-gap-baseline.md`
- `docs/prd-integrated-document-viewer-platform.md`
- `docs/trd-integrated-document-viewer-platform.md`
- `docs/diagrams/submit-flow.md`
- `docs/diagrams/submit-policy-adapter-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/diagrams/retry-deadletter-flow.md`
- `docs/engineering/acceptance-criteria.md`
- `docs/workflow/one-day-delivery-plan.md`
