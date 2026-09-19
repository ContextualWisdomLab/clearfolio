# ADR-0002: Separate tenant authorization from signed artifact-delivery authority

Status: Accepted
Implementation maturity: `IMPLEMENTED_ON_MAIN` for canonical artifact route; direct-download alignment is `ACTIVE_PR`

## Context and drivers

A caller that may read job metadata should not automatically receive document bytes. Tenant permission and a signed artifact token protect different threats: the former authorizes the actor within a tenant, while the latter binds a short-lived read to a specific document, artifact checksum, purpose, scope, issuance record and revocation state.

## Alternatives

1. Authorize artifact bytes with `job:read` only.
2. Authorize bytes with `artifact:read` only.
3. Require endpoint-appropriate tenant permission plus the signed artifact-delivery contract, with cross-tenant concealment.

## Decision

Choose alternative 3. `ArtifactLinkService` is the signed-delivery authority. Artifact token verification covers signature, expiry, read scope, document binding, issued-token ledger state, revocation, tenant ownership and artifact checksum. Canonical delivery supports only a single HTTP byte range and records controlled read-audit evidence. Direct-download convenience routes must not bypass these controls.

## Consequences

Clients first obtain or receive a valid short-lived artifact link/token. Additional requests and token lifecycle complexity are accepted in exchange for revocation, purpose binding, checksum binding and auditability.

## Failure and recovery

Missing/expired/unknown/revoked/mismatched tokens fail closed. Cross-tenant resources remain concealed. Invalid or unsatisfiable range requests return controlled range failure without silently serving whole bytes. A new link can be issued after legitimate expiry/revocation according to authorization policy.

## Security and privacy

Raw tokens are secrets and are never logged or persisted as ordinary audit content. Read events contain controlled identifiers/trace/range/status only. Dedicated artifact permission is least privilege but is not itself a substitute for signed token verification.

## Compatibility and migration

Viewer links already use the signed route. Direct-download callers that previously relied only on tenant permission must migrate through an explicit versioned client/contract change when the active remediation integrates; permission-only behavior is not preserved as a compatibility fallback.

The rollout order is:

1. make signed artifact-link issuance and canonical token verification available to every supported client;
2. update client/consumer contract tests to send a valid signed token and to handle the documented `401`/`403`/`404`/`409`/`416` failure semantics without probing resource existence;
3. deploy the direct-download implementation that applies the same signature, expiry, scope, document, tenant, checksum, issued-ledger, revocation, single-Range and read-audit authority;
4. remove any legacy permission-only expectation only after the migrated clients are proven against the integrated contract.

Rollback may revert a client rollout while the old server behavior is still intentionally available during a bounded migration window, but once the signed-delivery enforcement is the protected contract, rollback must not re-enable permission-only byte access. Any incompatible response-shape or endpoint-semantic change requires an explicit API version/migration note and consumer tests.

## Tests and acceptance

- missing permission and cross-tenant concealment;
- missing/malformed/expired/scope/doc/checksum/ledger token rejection;
- revocation rejection;
- full and single-range reads;
- multi-range/invalid/unsatisfiable controlled failure;
- versioned client/contract tests for migrated direct-download success and failure responses;
- rollout compatibility tests proving both canonical signed delivery and migrated direct-download semantics before legacy assumptions are retired;
- read-audit evidence;
- exact-head security/fuzz/coverage gates.

## Rollback / supersession

Do not roll back to permission-only document-byte access. A successor mechanism must preserve equivalent or stronger short-lived binding, revocation, integrity, tenant and audit properties.
