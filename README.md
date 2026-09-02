# Clearfolio Viewer

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/ContextualWisdomLab/clearfolio)

Clearfolio is an independently deployable secure document-conversion and viewing service. It can also compose with naruon and other ContextualWisdomLab hosts through explicit versioned APIs without shared application-database ownership.

The current runtime is Java 21 / Spring Boot WebFlux. Upload submission is non-blocking, conversion lifecycle work is delegated to bounded background workers, viewer bootstrap and artifact access are tenant-scoped, and the canonical artifact route uses short-lived signed token, revocation, integrity, Range, and read-audit controls.

## Product truth

- Validated PDF passthrough and PDF.js viewing are implemented on protected main.
- Non-PDF `PdfBoxArtifactGenerator` output is a **development/demo one-page placeholder**, not a faithful DOCX/HWP/Office conversion claim.
- The default conversion-job repository remains process-local. Filesystem artifacts and selected append-only ledgers provide only their documented local restart continuity, not a durable distributed job system.
- Production identity-provider integration, real Office fidelity, durable distributed jobs/backpressure/cancellation, full OpenTelemetry/SLO evidence, and release-grade remote persistence remain product gaps or active work as classified in the canonical docs.
- Active pull-request behavior is never treated as protected-main functionality until integrated and revalidated.

## Quick start

Run repository acceptance first:

```bash
mvn -B --no-transfer-progress verify
python3 scripts/verify_maven_test_reports.py
python3 -m unittest discover -s scripts
```

Then run the local application smoke in two terminals so the foreground Spring process remains alive:

```bash
# Terminal 1
mvn spring-boot:run
```

```bash
# Terminal 2, after Terminal 1 reports the application started
curl -fsS http://localhost:8080/healthz
```

Focused tests may be used during RED/GREEN development, but final local acceptance uses `mvn verify` plus the test-report verifier. Merge/release acceptance requires current exact-head GitHub evidence according to repository policy.

## Primary API surfaces

- `POST /api/v1/convert/jobs` — bounded asynchronous upload/submit.
- `GET /api/v1/convert/jobs/{jobId}` — tenant-scoped lifecycle status.
- `POST /api/v1/convert/jobs/{jobId}/retry` — controlled dead-letter retry.
- `GET /viewer/{docId}` — canonical HTML/PDF.js viewer entry.
- `GET /api/v1/viewer/{docId}` and compatibility alias — protected viewer bootstrap.
- `POST /api/v1/viewer/{docId}/artifact-links` — create a short-lived tenant-bound signed artifact link.
- `POST /api/v1/viewer/artifact-links/{tokenId}/revoke` — revoke an issued artifact token when authorized.
- `GET /api/v1/viewer/{docId}/artifact-read-events` — tenant-scoped artifact-read audit evidence.
- `GET /artifacts/{docId}.pdf` — canonical signed PDF byte delivery with zero-or-one HTTP Range support.
- `GET /api/v1/convert/jobs/{jobId}/download` — direct-download convenience path; its final accepted security contract is the canonical signed artifact-delivery authority, not permission-only byte access.
- analytics/admin/health surfaces are documented in `docs/API_CONTRACT.md` and live source; some stronger admin/readiness contracts remain active-PR work and are explicitly labelled there.

Protected JSON/document APIs use Clearfolio tenant/subject/permission authority. The current buyer-demo scaffold can carry header-based claims and configured signatures; it is not a substitute for production IdP/OIDC issuer, audience, expiry, revocation, and enterprise role mapping.

## Lifecycle

Public conversion lifecycle values are:

```text
SUBMITTED → PROCESSING → SUCCEEDED
                 ↘ retry/recovery → SUBMITTED
                 ↘ terminal/exhausted → FAILED
FAILED + dead-letter evidence → authorized retry → SUBMITTED
```

Retry exhaustion remains `FAILED` with dead-letter evidence rather than a separate public terminal enum.

## Security highlights

- uploaded documents and document-derived strings are untrusted;
- blocked/oversized/malformed requests fail closed;
- cross-tenant resources are concealed according to the API contract;
- document-byte permission and signed artifact-token authority are separate controls;
- canonical signed reads bind tenant, subject, document, scope, purpose, checksum, issuance, expiry and revocation and record controlled read evidence;
- raw tokens, signatures and unnecessary sensitive identifiers must not be logged;
- active-content/macro execution and implicit external network access are not accepted deterministic-conversion authority paths;
- cryptographic purposes use separated reviewed secrets when required by the integrated revision.

See `SECURITY.md`, `docs/THREAT_MODEL.md`, and the ADR index for the complete boundary.

## Standalone and CWL composition

Clearfolio owns its conversion/viewer behavior, tenant enforcement, artifact-delivery rules, state interfaces and product evidence. A host such as naruon may own higher-level user workflow, upstream identity/federation and deployment composition, but communicates over explicit contracts and does not mutate Clearfolio application persistence directly.

`contextual-orchestrator` is optional for genuinely model-backed features and is not authority for deterministic conversion, authorization, fidelity measurement, merge, or release.

## Canonical documentation

Start here rather than reconstructing the product from dated plans or PR bodies:

- [`docs/PRD.md`](docs/PRD.md) — product requirements, users, maturity and fidelity/release outcomes.
- [`docs/TRD.md`](docs/TRD.md) — technical requirements and implemented/active/planned boundaries.
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — system/trust/ownership architecture.
- [`docs/adr/README.md`](docs/adr/README.md) — architecture decisions and implementation maturity.
- [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) — logical ERD and persistence ownership.
- [`docs/UML.md`](docs/UML.md) — component/sequence/state/recovery/deployment diagrams.
- [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) — public API and MSA contract authority.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — product threat model.
- [`docs/TEST_STRATEGY.md`](docs/TEST_STRATEGY.md) — realistic TDD/security/fidelity/recovery strategy.
- [`docs/OPERABILITY.md`](docs/OPERABILITY.md) — startup, degraded mode, incident, backup/restore and release operations.
- [`docs/TRACEABILITY.md`](docs/TRACEABILITY.md) — requirement/ADR to implementation/test/PR/evidence mapping.
- [`docs/DOCUMENTATION_ASSESSMENT.md`](docs/DOCUMENTATION_ASSESSMENT.md) — documentation completeness and drift assessment.
- [`docs/engineering/acceptance-criteria.md`](docs/engineering/acceptance-criteria.md) — executable acceptance policy.

Legacy `docs/prd-integrated-document-viewer-platform.md`, `docs/trd-integrated-document-viewer-platform.md`, dated delivery plans, feature design notes and `docs/qa/evidence/` remain useful history/evidence. They do not override the canonical spine or live protected code.

## Development automation

Clearfolio's dedicated commercial loop is execution-first and work-conserving: an approval wait, queued check, one RCA, one commit, one documentation update or one merge blocks only the affected action while other safe work continues. Autonomous product development uses an immutably pinned OpenCode Agent with GitHub Secret `NVIDIA_NIM_API_KEY`, never `COPILOT_GITHUB_TOKEN` as a development-model credential, and preserves independent review/merge authority.

Central ContextualWisdomLab `.github` owns privileged PR-maintenance/review/merge control-plane behavior. Clearfolio must not copy that privileged implementation into the product repository.

## Release boundary

Do not release from a feature PR merely because its tests are green. Release only from an exact integrated protected head after CI, security, exact coverage/docstrings, realistic document-fidelity acceptance, accessibility, packaging, SBOM/provenance, reproducibility, API/schema compatibility, migrations/rollback/recovery where applicable, independent review, and protected-main operational acceptance all pass.
