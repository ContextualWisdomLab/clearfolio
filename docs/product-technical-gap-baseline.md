# Product and Technical Gap Baseline

Last code-current verification: 2026-09-02
Verification branch: `fix/admin-auth-tenant-isolation-7430428408399025558`
Verification head at authoring: `42831e05f6bd4bac980a4fb07a7389338059ace5`
Integration base at authoring: `main@06633a25109c62e24a7015ae04fb9f6e0a246f7e`

This document is the repository-level, code-current baseline for product responsibility,
DDD boundaries, executable quality evidence, buyer-visible gaps, and remediation state.
A pull request head, model review, package build, or conformance result is evidence about
that exact candidate only; it is not release, production, authorization, certification,
or customer-adoption authority.

## Product responsibility and buyer outcome

Clearfolio Viewer accepts documents, records conversion lifecycle state, runs bounded
asynchronous conversion work, serves PDF artifacts and viewer bootstrap state, and
provides tenant-authorized administrative recovery operations. The buyer-visible outcome
is a predictable document-viewing entry point whose conversion and recovery behavior does
not leak another tenant's resources or expose recoverable operator identifiers.

The repository does **not** currently claim completed downstream S2S preview-session
orchestration, durable PostgreSQL job persistence, or production-grade native conversion
for every office-document format. Placeholder/PDF-passthrough behavior and the in-memory
job repository remain explicit constraints in the current architecture.

## DDD executable baseline

### Subdomains and bounded contexts

| Classification | Bounded context | Owned responsibility | Explicit non-responsibility |
| --- | --- | --- | --- |
| Core | Document Conversion & Viewing | Conversion-job lifecycle, async conversion initiation, viewer readiness/bootstrap, document artifact availability | Enterprise identity-provider authority, external gateway routing, customer workflow policy |
| Supporting | Conversion Execution & Recovery | Worker claim/retry/dead-letter/recovery behavior and processing lease semantics | HTTP authorization policy, artifact rendering UI |
| Supporting | Artifact Delivery | PDF artifact persistence/read delivery and PDF passthrough | Conversion-job ownership authority |
| Supporting | Tenant Administration | Permission-gated list/delete/retry use cases over tenant-owned conversion jobs | Raw identity storage, cryptographic key ownership, global job browsing |
| Generic | Access & Audit Boundary | Verified `TenantContext`, permission checks, privacy-safe retry-operator audit identity | Domain retry eligibility or business approval decisions |

### Context map and dependency direction

```text
HTTP adapters
  -> TenantAccessService (request authentication/permission)
  -> DocumentConversionService (tenant-scoped application port)
       -> ConversionJobRepository (aggregate persistence port)
       -> ConversionJobStateStore (atomic lifecycle-transition port)
       -> ConversionWorker (async execution port)
       -> ArtifactStore (artifact port)
  -> RetryOperatorIdentityPort (audit-identity privacy port)
       -> HmacRetryOperatorIdentityAdapter
            -> ConversionProperties audit pseudonym key/version
```

Dependency direction is inward toward application/domain contracts. `AdminController`
must not fetch a global job collection or unscoped aggregate and then reconstruct tenant
ownership itself. Cryptographic pseudonymization is isolated behind
`RetryOperatorIdentityPort`; the resulting string is audit correlation metadata only and
must never become an authentication principal or authorization decision.

### Ubiquitous language and tactical model

- **Conversion Job**: aggregate root that owns conversion lifecycle and retry/dead-letter
  invariants.
- **Tenant Context**: verified value object carrying tenant, subject, and granted
  permissions for one request/application action.
- **Dead-lettered Job**: conversion job whose automatic retry budget is exhausted and that
  is eligible only for governed manual retry.
- **Retry Operator Identity**: privacy-safe, versioned audit correlation value. It is not a
  customer identity source of truth.
- **Policy Override**: separately authenticated exception input for blocked-format policy;
  it is independent from retry authorization.
- **Artifact**: converted/PDF-passthrough bytes associated with a conversion job; artifact
  storage does not own job authorization.

Current aggregate/application invariants:

1. Protected admin behavior authenticates and checks the required permission before a
   tenant-scoped application query or command.
2. Missing and cross-tenant job identifiers are indistinguishable to the admin HTTP
   surface and map to `404` rather than providing an existence oracle.
3. Tenant-facing adapters use tenant-scoped `DocumentConversionService` operations;
   ownership is not duplicated as controller-local filtering.
4. `DefaultDocumentConversionService` resolves tenant ownership through
   `ConversionJobRepository` before returning or mutating an aggregate.
5. Manual retry changes lifecycle state only when `ConversionJobStateStore` accepts the
   dead-letter transition; otherwise it returns a typed non-eligible outcome.
6. Retry audit identity uses keyed HMAC with a retry-specific domain and key version. A
   missing correlation key produces a non-correlatable unavailable marker, never a raw
   subject or unkeyed subject digest.
7. Audit correlation metadata is not authorization evidence and must not be promoted into
   a business approval, risk acceptance, or compliance truth.

## Current PR #541 security/SOLID/TDD repair

The active candidate repairs a CRITICAL broken-access-control path and two design defects:

- missing admin request authentication/permission and tenant isolation;
- dictionary-recoverable unkeyed SHA-256 retry-operator identifiers;
- HTTP-layer ownership reconstruction from global/unscoped application queries.

TDD evidence was established before the corresponding production repairs:

- privacy RED predecessor `2ad42520d5906a66f801b44ce29a0c4c13fcb6e0`
  rejects the known unkeyed digest produced by that predecessor's production code;
- architecture RED predecessor `9faef44113baa24413254c4c532db87888e9510a`
  requires tenant-scoped application list/retry contracts before those contracts exist.

The causal design applies SRP/DIP/ISP by separating the HTTP adapter, tenant-aware
application port, persistence port, lifecycle state port, and retry-audit identity port.
The repository does not claim GREEN until all required checks on the unchanged current
head terminate successfully; queued, skipped, cancelled, absent, predecessor, and
model-only evidence are non-passing.

## Buyer-visible and technical gaps

| Gap | Buyer / operational impact | Current evidence | Required next acceptance | State |
| --- | --- | --- | --- | --- |
| Admin tenant isolation and privacy-safe retry audit identity | Prevents cross-tenant exposure/mutation and offline recovery of low-entropy operator IDs | PR #541 exact-head implementation and production-boundary tests | Unchanged exact head must pass full repository/security/coverage/Javadoc/package/provenance gates and then normal protected-branch review/merge governance | In progress |
| Durable conversion-job persistence | Restart/concurrency behavior remains bounded by in-memory repository except explicit recovery abstractions | `ConversionJobRepository`, `ConversionJobStateStore`, durable repository plan | PostgreSQL adapter with tenant-native queries, idempotent UPSERT/lifecycle transitions, realistic concurrency/recovery tests, 3NF schema and lock contract | Planned |
| Native document conversion | Non-PDF sources still use placeholder generation rather than full office-format fidelity | `PdfBoxArtifactGenerator`, architecture docs | Format-specific adapter boundaries plus representative real-document fidelity tests and safe resource limits | Planned |
| Downstream S2S preview-session orchestration | End-to-end enterprise/mobile preview chain is not yet product-complete | `docs/diagrams/preview-flow.md`, architecture target chain | Explicit session/authn/authz contract, failure/retry/timeout evidence and deployment ownership | Planned |
| Viewer accessibility/resilience evidence | Existing viewer behavior must remain demonstrably usable across current desktop/mobile/intermediate surfaces and failure states | Viewer UI/controller tests and design evidence elsewhere in repo | Current-head WCAG 2.2 AA keyboard/focus/screen-reader/touch plus loading/empty/error/offline/permission/stale/retry screenshots/evidence | Open evidence gap |
| Context Graph / EA projection | Repository changes are not automatically authoritative enterprise-architecture facts | No direct cross-service SQL or copied EA tables in this slice | On a deployable component/interface/version lifecycle change, emit the then-released versioned Context Assertion/CloudEvent contract and request EA owner-path projection with provenance | Conditional |

## Persistence and data contract

The current production repository remains in-memory. Any durable database implementation
must preserve these contracts before it can replace the current adapter:

- relational objects use descriptive two-or-more-word `snake_case` names;
- schema is normalized to at least 3NF unless a measured, documented exception is
  required;
- tenant ownership is present in every externally addressable job lookup/mutation path;
- dedupe/store and lifecycle transitions are idempotent under concurrent attempts;
- mutation transactions are short and do not contain external conversion/network calls;
- lock acquisition, retry, processing lease, stale-worker recovery, and failure semantics
  are tested under concurrent workers;
- read scaling must not weaken strong-consistency requirements for lifecycle mutation.

## Security and privacy traceability

The active repair is aligned with current authoritative guidance as follows:

- OWASP ASVS 5.0.0 is the current released ASVS baseline (released 2025-05-30). Its
  authorization requirements reinforce enforcing access decisions at protected resource
  boundaries rather than trusting caller-controlled identifiers.
- NIST SP 800-53 Rev. 5, including the current 5.2.0 supplemental release, provides the
  control baseline for access enforcement and audit/accountability assurance. This
  repository maps tenant-scoped application enforcement to AC-family intent and keeps
  privacy-safe audit correlation distinct from authorization authority.
- HMAC follows the keyed construction described by RFC 2104. Clearfolio adds an explicit
  application-domain separator and versioned dedicated audit key so identical subject
  identifiers are not reused as the same audit fingerprint across unrelated purposes.

These references support engineering controls; they are not claims that Clearfolio is
certified or that a particular deployment satisfies a compliance framework.

### References (APA 7th)

Joint Task Force. (2020). *Security and privacy controls for information systems and
organizations (NIST Special Publication 800-53, Revision 5)*. National Institute of
Standards and Technology. https://doi.org/10.6028/NIST.SP.800-53r5

Krawczyk, H., Bellare, M., & Canetti, R. (1997). *HMAC: Keyed-hashing for message
authentication (RFC 2104)*. RFC Editor. https://doi.org/10.17487/RFC2104

OWASP Foundation. (2025). *OWASP Application Security Verification Standard (ASVS),
version 5.0.0*. https://owasp.org/www-project-application-security-verification-standard/

## Evidence discipline and release gate

For every behavior-changing PR, the valid evidence chain is:

```text
current finding -> production-boundary RED -> smallest causal production repair
-> focused GREEN -> full exact-head GREEN -> security/coverage/docs/package/provenance
-> live review/ruleset verification -> normal protected merge -> post-merge verification
```

Do not reuse checks from a predecessor head, an earlier stack base, or a different merge
candidate. A review comment marked resolved is not a test result. Compatibility, package,
conformance, or provenance evidence is not business authorization. Any future release
must bind source SHA, artifact identity, dependency/security evidence, and the live
protected integration branch.
