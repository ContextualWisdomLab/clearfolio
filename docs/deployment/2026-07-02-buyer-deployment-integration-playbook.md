# Buyer Deployment and Integration Playbook

Date: 2026-07-02

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
- issued links, revocations, artifact reads, and KPI exports can survive a
  single-process restart through local append-only evidence ledgers;
- the same Maven, JavaDoc, coverage, Markdown, SAST, SBOM, and license-policy
  gates remain attached to PR #74.

The deployment cannot yet prove:

- production OIDC/JWT issuer, audience, expiry, `kid`, and role validation;
- centralized durable job, artifact, revocation, audit, or analytics storage;
- legal approval for the six review-required SBOM components;
- a packaged Power Platform connector.

## Runtime Profile

Use the `buyer-demo` Spring profile for a buyer sandbox:

```bash
mkdir -p .clearfolio/buyer-demo

export SPRING_PROFILES_ACTIVE=buyer-demo
export CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET="replace-with-gateway-shared-secret"
export CLEARFOLIO_ARTIFACT_TOKEN_SECRET="replace-with-artifact-token-secret"
export CLEARFOLIO_ARTIFACT_LINK_LEDGER_PATH="$PWD/.clearfolio/buyer-demo/artifact-link-ledger.log"
export CLEARFOLIO_ANALYTICS_SNAPSHOT_LEDGER_PATH="$PWD/.clearfolio/buyer-demo/kpi-snapshot-ledger.log"
export CLEARFOLIO_FRAME_ANCESTORS="self"

mvn spring-boot:run
```

The profile file is
`src/main/resources/application-buyer-demo.yml`. It uses environment variables
only; no secret value is committed.

For a Power Platform embedding test, replace `CLEARFOLIO_FRAME_ANCESTORS` with
the exact buyer allowlist after the gateway hostname is known. Keep it narrow;
do not use a wildcard until a security owner explicitly accepts that risk.

## Gateway Claim Contract

When `CLEARFOLIO_TENANT_CLAIMS_HMAC_SECRET` is set, every protected JSON API
call must include:

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

Buyer-demo permission set:

```text
job:create,job:read,job:retry,viewer:read,artifact-link:create,analytics:read
```

Production role mapping should later replace this scaffold with validated
gateway or OIDC claims. Do not hand-roll JWT parsing in this service.

## Integration Flow

1. Buyer browser, Power Platform, or internal workflow authenticates at the
   buyer-controlled gateway.
2. Gateway maps the principal to Clearfolio tenant id, subject id, and
   permissions.
3. Gateway signs the Clearfolio headers and forwards requests to
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

For PR evidence refresh, use the existing evidence folder:

```bash
mvn -DskipTests compile
mvn test
mvn -q -DskipTests javadoc:javadoc
python3 scripts/check_sbom_license_policy.py \
  --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json \
  --policy docs/security/2026-07-02-license-policy.json
```

Buyer-release mode must add `--require-no-review` only after legal approval or
dependency replacement clears all review-required components.

## Diligence Handoff Checklist

Before a buyer sandbox is shown, attach:

- PR #74 URL and latest head SHA;
- `docs/qa/evidence/2026-07-02-krw2b-sale-readiness/README.md`;
- this playbook;
- `docs/deployment/clearfolio-buyer-connector.openapi.yaml`;
- `src/main/resources/application-buyer-demo.yml`;
- `docs/security/2026-07-02-auth-tenant-model.md`;
- `docs/security/2026-07-02-signed-artifact-link-design.md`;
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

- legal approve, replace, or remove decisions for the six review-required SBOM
  components;
- validated gateway or OIDC JWT issuer, audience, expiry, key rotation, and role
  mapping;
- durable conversion job repository with persisted state transitions;
- durable object store metadata, token revocation, and artifact read audit
  persistence;
- durable metrics event stream and daily KPI projections;
- buyer-specific `frame-ancestors` allowlist and security owner approval;
- connector seed imported and tested against the buyer's actual gateway and
  Power Platform tenant.
