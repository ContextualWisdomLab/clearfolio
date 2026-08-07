# Buyer Deployment and Integration Playbook

Date: 2026-07-02  
Last updated: 2026-08-07

This playbook turns the current Clearfolio Viewer sale-readiness slice into a repeatable buyer sandbox. The runtime is Spring Boot and WebFlux with a browser shell, gateway-signed tenant headers, signed artifact links, filesystem-backed artifacts, append-only evidence ledgers, a receipt-first deletion worker, and mandatory exact-head repository gates. It does not claim production OIDC/JWT, a distributed database/object-store transaction, external legal sign-off, or a packaged Power Platform connector.

## Buyer-ready claim boundary

The current sandbox can prove:

- document upload, asynchronous conversion, status polling, preview, KPI evidence, dead-letter retry, and tenant-scoped administration;
- gateway-signed tenant claims with action-specific least privilege;
- tenant-owned document bytes require dedicated `artifact:read`, while preview links require signed tenant-bound artifact tokens;
- signing keys come from a mounted Spring config tree rather than secret-valued runtime environment placeholders;
- filesystem artifacts, issued-link evidence, revocations, artifact reads, KPI exports, and deletion receipts survive a single-process restart;
- authorized deletion persists intent before the first artifact read, binds an exact generation before metadata tombstoning, and performs bounded scheduled recovery after cleanup failure;
- exact-head Maven, Javadoc, coverage, script, SAST, SBOM, attribution, and license-policy gates remain fail closed.

The sandbox cannot yet prove:

- production OIDC/JWT issuer, audience, expiry, `kid`, rotation, and role validation;
- centralized durable job, artifact, revocation, audit, and analytics storage across multiple instances;
- a cross-resource transactional outbox spanning a durable database and remote object store;
- a distributed generation fence or remote-object-store atomicity;
- final legal review of attribution and redistribution evidence;
- a buyer-specific packaged connector.

## Runtime profile

Use the `buyer-demo` Spring profile. Runtime key material is loaded from a Spring Boot config-tree mount. `CLEARFOLIO_SECRET_CONFIG_DIR` selects that mount and is not secret. Provision the mounted files `clearfolio.tenant-claims.hmac-secret` and `clearfolio.artifact-token.secret` independently through the deployment platform's secret manager. The tenant-claims key must contain at least 32 UTF-8 bytes for privileged administrative APIs.

For a local sandbox:

```bash
umask 077
mkdir -p .clearfolio/buyer-demo/secrets
mkdir -p .clearfolio/buyer-demo/data/artifacts
openssl rand -base64 48 \
  > .clearfolio/buyer-demo/secrets/clearfolio.tenant-claims.hmac-secret
openssl rand -base64 48 \
  > .clearfolio/buyer-demo/secrets/clearfolio.artifact-token.secret

export SPRING_PROFILES_ACTIVE=buyer-demo
export CLEARFOLIO_SECRET_CONFIG_DIR="$PWD/.clearfolio/buyer-demo/secrets/"
export CLEARFOLIO_ARTIFACT_LINK_LEDGER_PATH="$PWD/.clearfolio/buyer-demo/artifact-link-ledger.log"
export CLEARFOLIO_ANALYTICS_SNAPSHOT_LEDGER_PATH="$PWD/.clearfolio/buyer-demo/kpi-snapshot-ledger.log"
export CLEARFOLIO_ARTIFACT_DELETION_LEDGER_PATH="$PWD/.clearfolio/buyer-demo/artifact-deletion-receipts.log"
export CLEARFOLIO_ARTIFACT_STORE_ROOT_DIR="$PWD/.clearfolio/buyer-demo/data/artifacts"
export CLEARFOLIO_FRAME_ANCESTORS="self"

mvn spring-boot:run
```

Spring relaxed binding maps the non-secret path variables to the corresponding `clearfolio.*` properties. The repository defaults remain `data/artifacts` for PDF bytes and `data/artifact-deletion-receipts.log` for deletion receipt snapshots. The receipt file and artifact root must reside on durable owner-restricted storage in shared environments. Do not put either path on an ephemeral container layer when restart recovery is required.

`src/main/resources/application-buyer-demo.yml` may use environment variables for non-secret operational settings. Neither HMAC key is bound from a secret-valued environment placeholder. Both are read from the common config-tree import in `application.yml`; the environment selects only the bootstrap directory.

## Durable deletion and recovery operation

Tenant-owned deletion is routed through `DurableDocumentDeletionService` and `ArtifactDeletionCoordinator`.

1. The service acquires the shared per-job lifecycle lock.
2. It forces a durable receipt with a controlled `pending` checksum marker before the first artifact read.
3. If that read fails, metadata remains live and the receipt remains recoverable. The failure is represented by a controlled low-cardinality code, not exception text.
4. A successful read binds the same receipt identity one way to the exact SHA-256 digest, or to the explicit confirmed-absence digest when the artifact is truly absent.
5. Only then may tenant-scoped metadata be tombstoned.
6. Cleanup compares the bound generation, deletes idempotently, and records completion or retryable failure.
7. Startup recovery and the fixed-delay worker process a bounded batch of incomplete receipts.

Defaults are a 30-second retry delay, a maximum of 100 receipts per pass, and a Spring scheduler pool of two threads. Per-job locks serialize the same generation while unrelated jobs can progress. Aggregate evidence is limited to completed, failed, pending, and execution-duration values; it carries no tenant, job, digest, path, filename, or exception-controlled dimension.

`LifecycleFencedArtifactStore` prevents a conversion worker from publishing another artifact after receipt acceptance. An in-flight publication that wins the lock first becomes the captured generation and is removed; publication after receipt acceptance fails closed.

This is a restart-safe single-process reference adapter, not a distributed transaction. Multi-instance production deployment remains blocked until adapters provide a cross-resource transactional outbox or equivalent idempotent consumer, durable uniqueness and tombstones, a distributed generation fence, object-version preconditions, remote-object-store atomicity or safely compensating semantics, bounded dead-letter recovery, and operator evidence.

## Gateway claim contract

When `clearfolio.tenant-claims.hmac-secret` is mounted, protected JSON APIs require:

- `X-Clearfolio-Tenant-Id`
- `X-Clearfolio-Subject-Id`
- `X-Clearfolio-Permissions`
- `X-Clearfolio-Claims-Issued-At`
- `X-Clearfolio-Claims-Signature`

The HMAC payload is the exact newline-joined form:

```text
tenantId
subjectId
canonicalPermissions
issuedAt
```

The signature is Base64URL HMAC-SHA256 without padding. The default timestamp skew is 300 seconds and is configurable with `CLEARFOLIO_TENANT_CLAIMS_MAX_SKEW_SECONDS`.

The verifier derives `canonicalPermissions` by splitting on commas, removing NUL, stripping whitespace, dropping empty values, de-duplicating in first-seen order, and joining with commas. Tenant and subject values are sanitized before verification as well. The gateway must send and sign canonical values, not a differently formatted raw header.

The authenticated gateway must strip untrusted inbound `X-Clearfolio-*` headers before mapping the principal, constructing claims, signing, and forwarding. Browsers and external API clients are not trusted issuers.

Example user permission set:

```text
job:create,job:read,job:retry,viewer:read,artifact:read,artifact-link:create,analytics:read
```

Example tenant operator additions:

```text
admin:read,admin:write
```

Production role mapping must later replace this scaffold with validated gateway or OIDC claims. Do not hand-roll JWT parsing in this service.

For `SPRING_PROFILES_ACTIVE=production`, startup fails unless the config-tree mount supplies a sufficiently strong tenant-claims key. Setting only the bootstrap directory does not enable signed claims. Artifact-link issuance reads a distinct strong `clearfolio.artifact-token.secret`; do not restore the retired direct environment binding.

## Integration flow

1. A buyer browser, Power Platform client, or internal workflow authenticates at the buyer-controlled gateway.
2. The gateway strips untrusted Clearfolio headers, maps the principal to tenant, subject, and permissions, canonicalizes them, signs them, and forwards the request.
3. Clearfolio verifies the signature, freshness, action permission, and tenant ownership.
4. `POST /api/v1/convert/jobs` creates a durable asynchronous job identity and the worker produces a fenced PDF generation.
5. Status and viewer bootstrap remain tenant scoped; direct download additionally requires `artifact:read`.
6. PDF.js reads only a same-origin artifact authorized by a valid tenant-bound token.
7. Optional ledgers capture issued links, revocations, reads, KPI exports, and deletion receipts.
8. Tenant-owned deletion persists intent, binds the artifact generation, tombstones metadata, and completes or retries physical cleanup.
9. Restart with the same paths replays the append-only evidence and resumes bounded incomplete cleanup.

## API surface for a connector

| Connector step | Endpoint | Permission | Buyer evidence |
| --- | --- | --- | --- |
| Submit document | `POST /api/v1/convert/jobs` | `job:create` | Returns `202`, `jobId`, and `statusUrl`. |
| Poll lifecycle | `GET /api/v1/convert/jobs/{jobId}` | `job:read` | Shows status, attempts, retry time, and dead-letter state. |
| Download result | `GET /api/v1/convert/jobs/{jobId}/download` | `artifact:read` | Returns tenant-owned PDF bytes only. |
| Open viewer | `GET /viewer/{docId}` | HTML shell | Protected JSON APIs determine visible data. |
| Bootstrap preview | `GET /api/v1/viewer/{docId}` | `viewer:read` | Returns a short-lived signed artifact URL. |
| Create artifact link | `POST /api/v1/viewer/{docId}/artifact-links` | `artifact-link:create` | Produces tenant-bound token metadata. |
| Retry dead letter | `POST /api/v1/convert/jobs/{jobId}/retry` | `job:retry` | Records operator recovery evidence. |
| List tenant jobs | `GET /api/v1/admin/convert/jobs` | `admin:read` | Returns only the authenticated tenant's scope. |
| Delete tenant job | `DELETE /api/v1/admin/convert/jobs/{jobId}` | `admin:write` | Enters the durable deletion lifecycle. |
| Retry tenant job | `POST /api/v1/admin/convert/jobs/{jobId}/retry` | `admin:write` | Uses an atomic tenant-scoped retry transition. |
| Read KPIs | `GET /api/v1/analytics/kpi-snapshot` | `analytics:read` | Shows bounded operational evidence. |

## Buyer sandbox smoke test

After startup:

```bash
curl -sS http://localhost:8080/healthz
curl -sS http://localhost:8080/ | head
```

Complete one realistic browser and operator flow:

1. Upload a small supported office document.
2. Confirm the status reaches `SUCCEEDED`.
3. Open the signed viewer and direct-download path with the correct permissions.
4. Confirm the KPI evidence updates.
5. Exercise a dead-letter retry when available.
6. Delete an owned job and confirm a receipt snapshot is appended.
7. Inject one artifact-store failure in a controlled test environment and confirm metadata is not lost before exact digest binding and incomplete cleanup remains pending.
8. Restart with the same artifact and ledger paths, then confirm bounded recovery completes the cleanup.
9. Confirm cross-tenant UUID requests remain concealed and do not touch artifact storage.

For exact-head engineering evidence:

```bash
mvn -B --no-transfer-progress verify
python3 scripts/verify_maven_test_reports.py
python3 -m unittest discover -s scripts
python3 scripts/check_sbom_license_policy.py \
  --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json \
  --policy docs/security/2026-07-02-license-policy.json \
  --require-no-review
python3 scripts/render_third_party_attribution.py \
  --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json \
  --output docs/legal/2026-07-03-third-party-attribution.md \
  --check
python3 scripts/check_buyer_dataroom_manifest.py \
  --manifest docs/diligence/2026-07-03-buyer-data-room-manifest.json
```

Do not substitute `mvn test`, a standalone Javadoc invocation, a synthetic-only result, or predecessor-head evidence. Buyer-release mode keeps `--require-no-review` enabled.

## Diligence handoff checklist

Before a buyer sandbox is shown, attach:

- the authoritative PR URL and exact head SHA;
- `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/README.md`;
- `docs/diligence/2026-07-03-buyer-data-room-manifest.json`;
- this playbook and `docs/deployment/clearfolio-buyer-connector.openapi.yaml`;
- `src/main/resources/application-buyer-demo.yml`;
- `docs/security/2026-07-02-auth-tenant-model.md`;
- `docs/security/2026-07-02-signed-artifact-link-design.md`;
- `docs/security/2026-08-05-administrative-authorization.md`;
- `docs/security/2026-08-06-durable-artifact-deletion-receipts.md`;
- `docs/legal/2026-07-03-third-party-attribution.md`;
- `docs/analytics/2026-07-02-durable-metrics-event-model.md`;
- the FigJam `Clearfolio Buyer Integration Deployment Flow` board.

The FigJam board mirrors the gateway, runtime, evidence-ledger, and diligence flow. Figma Code Connect is not used by this deployment slice.

## Connector seed

`docs/deployment/clearfolio-buyer-connector.openapi.yaml` is an OpenAPI 3.0 import seed for a buyer-owned gateway or Power Platform custom connector. It covers submission, status, retry, viewer bootstrap, signed-link creation and revocation, artifact-read audit lookup, and KPI evidence. It models the current signed-header adapter; it is not a validated production OIDC/JWT connector and has not been imported into a buyer tenant.

A buyer-specific package should be created only after gateway hostname, OIDC issuer, role mapping, deletion-status API contract, and `frame-ancestors` allowlist are known.

## Standalone and modular decision

No Maven multi-module split is required for this sandbox. The runtime contracts are injectable: a standalone deployment uses the filesystem adapters, while naruon or another CWL host can replace repository, artifact, receipt, audit, and claim adapters. Replacement adapters must preserve tenant scoping, immutable generation identity, receipt-before-read ordering, one-way digest binding, generation fencing, idempotent recovery, and privacy-safe evidence.

## Production cutover gates

Do not promote the buyer sandbox until all of these gates close:

- exact-head CI, Security Scan, SAST, fuzz, complete coverage, warning-free Javadocs, SBOM, provenance, attribution, and buyer-release license policy are successful;
- final independent write-authorized review and legal review are complete;
- production config-tree mounts contain distinct strong signing keys;
- validated OIDC/JWT issuer, audience, expiry, rotation, and role mapping replace the header scaffold;
- a durable conversion-job repository enforces persisted state transitions and permanent generation uniqueness;
- the object store, token revocation, read audit, and deletion receipt/outbox adapters are durable and multi-instance safe;
- distributed generation fencing, object-version preconditions, remote-object-store recovery, dead-letter operations, and restore/rollback drills are evidenced;
- metrics and OpenTelemetry evidence are privacy safe and operationally actionable;
- the buyer-specific `frame-ancestors` allowlist and connector package are tested against the actual gateway and tenant.
