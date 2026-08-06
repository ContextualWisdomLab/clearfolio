# Buyer Deployment and Integration Playbook

Date: 2026-07-02  
Last updated: 2026-08-06

This playbook turns the current Clearfolio Viewer sale-readiness slice into a
repeatable buyer sandbox deployment. It is intentionally scoped to the current
runtime: Spring Boot, WebFlux JSON APIs, buyer-demo shell, gateway-signed tenant
headers, signed artifact links, local append-only evidence ledgers, and the
mandatory repository gates. It does not use Figma Code Connect and does not
claim production OIDC/JWT, durable database, durable object storage, or external
legal sign-off.

## Buyer-Ready Claim Boundary

The deployment can prove:

- a buyer can run the upload, conversion, preview, KPI, and operator recovery
  demo without adding a frontend framework;
- tenant-scoped JSON APIs can require gateway-signed Clearfolio headers when a
  shared HMAC secret is configured;
- preview artifacts require signed artifact tokens, not bare document ids;
- tenant-claim and artifact-token signing keys are read from a mounted Spring
  config tree rather than secret-valued runtime environment placeholders;
- issued links, revocations, artifact reads, and KPI exports can survive a
  single-process restart through local append-only evidence ledgers;
- the same Maven, JavaDoc, coverage, Markdown, SAST, SBOM, and license-policy
  gates remain attached to the exact current PR head.

The deployment cannot yet prove:

- production OIDC/JWT issuer, audience, expiry, `kid`, and role validation;
- centralized durable job, artifact, revocation, audit, analytics, or failed
  artifact-cleanup storage;
- restart-safe deletion receipts, transactional cleanup outbox processing,
  bounded retry, or deterministic orphan-artifact recovery evidence;
- final legal review of the attribution and redistribution package;
- a packaged Power Platform connector.

## Runtime Profile

Use the `buyer-demo` Spring profile for a buyer sandbox. Runtime key material is
loaded from a Spring Boot config-tree mount. `CLEARFOLIO_SECRET_CONFIG_DIR`
selects that mount and is not itself secret. The mounted files
`clearfolio.tenant-claims.hmac-secret` and
`clearfolio.artifact-token.secret` must be provisioned independently through the
deployment platform's secret manager in shared environments. The tenant-claims
key must contain at least 32 UTF-8 bytes for privileged administrative APIs.

For a local sandbox, create owner-readable config-tree files before startup:

```bash
umask 077
mkdir -p .clearfolio/buyer-demo/secrets
openssl rand -base64 48 \
  > .clearfolio/buyer-demo/secrets/clearfolio.tenant-claims.hmac-secret
openssl rand -base64 48 \
  > .clearfolio/buyer-demo/secrets/clearfolio.artifact-token.secret

export SPRING_PROFILES_ACTIVE=buyer-demo
export CLEARFOLIO_SECRET_CONFIG_DIR="$PWD/.clearfolio/buyer-demo/secrets/"
export CLEARFOLIO_ARTIFACT_LINK_LEDGER_PATH="$PWD/.clearfolio/buyer-demo/artifact-link-ledger.log"
export CLEARFOLIO_ANALYTICS_SNAPSHOT_LEDGER_PATH="$PWD/.clearfolio/buyer-demo/kpi-snapshot-ledger.log"
export CLEARFOLIO_FRAME_ANCESTORS="self"

mvn spring-boot:run
```

The profile file is
`src/main/resources/application-buyer-demo.yml`. Non-secret operational settings
may use environment variables. Neither HMAC key is bound from a secret-valued
runtime environment variable. Both are read from the shared config-tree import
in `application.yml`; the environment selects only the bootstrap directory.
`CLEARFOLIO_TENANT_CLAIMS_MAX_SKEW_SECONDS` remains a non-secret runtime setting.

An owned repository deletion precedes the current best-effort artifact removal.
If the artifact store rejects that removal, the job remains deleted and orphaned
artifact bytes can remain. This slice does not configure `ArtifactCleanupQueue`,
a retry cadence, deletion receipts, a cleanup outbox, or aggregate cleanup
metrics. Issue #263 owns the restart-safe cleanup subsystem and its deterministic
recovery evidence; production cutover remains blocked until that work is
integrated.

For a Power Platform embedding test, replace `CLEARFOLIO_FRAME_ANCESTORS` with
the exact buyer allowlist after the gateway hostname is known. Keep it narrow;
do not use a wildcard until a security owner explicitly accepts that risk.

## Gateway Claim Contract

When the mounted `clearfolio.tenant-claims.hmac-secret` property is present,
every protected JSON API call must include:

- `X-Clearfolio-Tenant-Id`
- `X-Clearfolio-Subject-Id`
- `X-Clearfolio-Permissions`
- `X-Clearfolio-Claims-Issued-At`
- `X-Clearfolio-Claims-Signature`

The HMAC payload is the exact newline-joined string:

```text
tenantId
subjectId
canonicalPermissions
issuedAt
```

The signature is Base64URL HMAC-SHA256 without padding. The default timestamp
skew window is 300 seconds and can be set with
`CLEARFOLIO_TENANT_CLAIMS_MAX_SKEW_SECONDS`.

`canonicalPermissions` is **not** the raw `X-Clearfolio-Permissions` header
value — the verifier re-derives it before checking the signature, so a buyer
gateway must sign the same derived form or every request returns `401`. The
derivation (see `TenantContext.permissionsOf` / `canonicalPermissions`) is:

1. split the header on `,`;
2. sanitize each entry — remove NUL, `strip()` surrounding whitespace, drop empties;
3. de-duplicate **preserving first-seen order** (backed by a `LinkedHashSet`);
4. re-join with `,`.

`tenantId` and `subjectId` are sanitized the same way before signing. So a
gateway must send **and sign** already-canonical values: e.g.
`" viewer:read , job:read,viewer:read "` must be signed as
`viewer:read,job:read`. Sign what the verifier will re-derive, not the raw
string.

The authenticated gateway must remove all untrusted inbound
`X-Clearfolio-*` claim headers before it maps the authenticated principal,
constructs canonical claims, signs them, and forwards the replacement header
set. Browsers and external API clients are not trusted claim issuers.

Buyer-demo permission set:

```text
job:create,job:read,job:retry,viewer:read,artifact-link:create,analytics:read
```

Production role mapping should later replace this scaffold with validated
gateway or OIDC claims. Do not hand-roll JWT parsing in this service.

For any environment that sets `SPRING_PROFILES_ACTIVE=production`, the service
fails startup unless the config-tree mount supplies a sufficiently strong
`clearfolio.tenant-claims.hmac-secret`. Setting only
`CLEARFOLIO_SECRET_CONFIG_DIR` without the required secret file does not enable
signed claims. The buyer-demo profile can still run unsigned for local
screenshots, but production cannot accidentally inherit that unsigned mode.
Artifact-link issuance likewise reads `clearfolio.artifact-token.secret` from
the same mount; production operators must provision a distinct strong key and
must not restore the retired `CLEARFOLIO_ARTIFACT_TOKEN_SECRET` runtime binding.

## Integration Flow

1. Buyer browser, Power Platform, or internal workflow authenticates at the
   buyer-controlled gateway.
2. Gateway strips untrusted inbound Clearfolio claim headers, maps the principal
   to Clearfolio tenant id, subject id, and permissions, and canonicalizes the
   mapped values.
3. Gateway signs the canonical Clearfolio headers and forwards requests to
   `POST /api/v1/convert/jobs`, status, viewer bootstrap, retry, artifact-link,
   and analytics APIs.
4. Clearfolio verifies the signed headers, enforces permissions, and hides
   cross-tenant resources.
5. The buyer-demo shell shows the upload, status, KPI evidence, and operator
   recovery path at `GET /`.
6. Viewer bootstrap returns a signed `previewResourcePath`.
7. PDF.js reads `/artifacts/{docId}.pdf` only with a valid artifact token.
8. Optional local ledgers capture issued links, revocations, artifact reads,
   and KPI snapshot exports for buyer evidence.
9. An owned job deletion attempts artifact removal only after tenant-scoped
   repository deletion. A removal failure is currently best effort, can leave
   orphaned bytes, and is not a durable retry or operational-evidence record.

## API Surface for a Connector

| Connector step | Endpoint | Permission | Buyer evidence |
| --- | --- | --- | --- |
| Submit document | `POST /api/v1/convert/jobs` | `job:create` | Returns `202`, `jobId`, `statusUrl`. |
| Poll lifecycle | `GET /api/v1/convert/jobs/{jobId}` | `job:read` | Shows status, attempts, retry time, dead-letter state. |
| Open viewer | `GET /viewer/{docId}` | HTML shell | Protected JSON APIs decide visible state. |
| Bootstrap preview | `GET /api/v1/viewer/{docId}` | `viewer:read` | Returns short-lived signed artifact URL. |
| Create artifact link | `POST /api/v1/viewer/{docId}/artifact-links` | `artifact-link:create` | Produces tenant-bound token metadata. |
| Retry dead letter | `POST /api/v1/convert/jobs/{jobId}/retry` | `job:retry` | Shows operator recovery evidence. |
| Read KPIs | `GET /api/v1/analytics/kpi-snapshot` | `analytics:read` | Shows runtime job count, success rate, P95 preview. |
| Read KPI exports | `GET /api/v1/analytics/kpi-snapshot-exports` | `analytics:read` | Shows exported buyer evidence without tenant id. |

## Buyer Sandbox Smoke

After the app starts, run:

```bash
curl -sS http://localhost:8080/healthz
curl -sS http://localhost:8080/ | head
```

Then complete one browser flow:

1. Open `http://localhost:8080/`.
2. Upload a small supported document.
3. Confirm the status row reaches `SUCCEEDED`.
4. Open the viewer link.
5. Confirm the KPI strip and KPI snapshot evidence panel update.
6. Trigger or inspect operator recovery evidence through a failed or
   dead-lettered job when available.
7. Restart the app with the same ledger paths and confirm exported evidence
   records are replayed.

For exact-head engineering evidence, use the same fail-closed lifecycle as CI:

```bash
mvn -B --no-transfer-progress verify
python3 scripts/verify_maven_test_reports.py
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

Do not substitute `mvn test`, a standalone Javadoc invocation, or a predecessor
head. Buyer-release mode must keep `--require-no-review` enabled so any future
review-required component fails before buyer handoff. The attribution `--check`
must also pass so the buyer data-room notice matches the current SBOM.

## Diligence Handoff Checklist

Before a buyer sandbox is shown, attach:

- the authoritative PR URL and exact current head SHA;
- `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/README.md`;
- `docs/diligence/2026-07-03-buyer-data-room-manifest.json`;
- this playbook;
- `docs/deployment/clearfolio-buyer-connector.openapi.yaml`;
- `src/main/resources/application-buyer-demo.yml`;
- `docs/security/2026-07-02-auth-tenant-model.md`;
- `docs/security/2026-07-02-signed-artifact-link-design.md`;
- `docs/legal/2026-07-03-third-party-attribution.md`;
- `docs/analytics/2026-07-02-durable-metrics-event-model.md`;
- FigJam board:
  <https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM>.

The FigJam board includes `Clearfolio Buyer Integration Deployment Flow`, which
mirrors this playbook's gateway, runtime, evidence-ledger, and diligence
artifact path. Figma Code Connect is not used.

## Connector Seed

The repository includes `docs/deployment/clearfolio-buyer-connector.openapi.yaml`
as an OpenAPI 3.0 import seed for a buyer-owned gateway or Power Platform custom
connector. It covers:

- document submission through multipart upload;
- conversion status polling;
- dead-letter retry;
- viewer bootstrap with signed artifact URL;
- signed artifact-link creation and revocation;
- artifact read audit lookup;
- KPI snapshot and KPI export lookup.

The seed intentionally models the current signed Clearfolio tenant-header
scaffold. It is not a validated production OIDC/JWT connector profile, and it
has not been imported into a buyer Power Platform tenant. A buyer-specific
connector package should be created only after the gateway hostname, OIDC issuer,
role mapping, and `frame-ancestors` allowlist are known.

## Library and Submodule Decision

No separate library, submodule, or Maven multi-module split is justified for
this deployment slice. The profile and playbook reduce buyer integration cost
without introducing versioning, release, or source-of-truth overhead. Revisit a
split only after a packaged connector, SDK, or second service consumes the same
contracts independently.

## Production Cutover Gates

The buyer sandbox should not be promoted to production until these gates close:

- buyer-release license-policy evidence remains green with
  `--require-no-review`, attribution drift check remains green, and final legal
  release review is obtained;
- `SPRING_PROFILES_ACTIVE=production` starts only when the config-tree mount
  contains distinct strong `clearfolio.tenant-claims.hmac-secret` and
  `clearfolio.artifact-token.secret` files, and later replaces the claim scaffold
  with validated OIDC/JWT claims;
- validated gateway or OIDC JWT issuer, audience, expiry, key rotation, and role
  mapping;
- durable conversion job repository with persisted state transitions and
  permanent UUID tombstone or lifecycle-generation uniqueness;
- durable object store metadata, token revocation, artifact read audit, and
  restart-safe artifact-cleanup queue or transactional outbox;
- durable metrics event stream and daily KPI projections;
- buyer-specific `frame-ancestors` allowlist and security owner approval;
- connector seed imported and tested against the buyer's actual gateway and
  Power Platform tenant.
