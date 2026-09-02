# ADR-0001: Preserve standalone Clearfolio and explicit MSA ownership boundaries

Status: Accepted
Implementation maturity: `PARTIAL` / `ACCEPTED_ARCHITECTURE`

## Context and drivers

Clearfolio must work as an independently deployable document-conversion/viewing service while also composing with naruon and other CWL hosts. Hidden runtime coupling or cross-service database access would make deployment, security review, rollback and acquisition diligence harder.

## Alternatives

1. Make Clearfolio a naruon-internal module with shared persistence.
2. Keep Clearfolio standalone but allow hosts to read/write its application database.
3. Keep Clearfolio independently operable and compose only through explicit versioned contracts.

## Decision

Choose alternative 3. Clearfolio owns document intake, conversion lifecycle, artifact-delivery rules, local authorization enforcement, its state interfaces and its evidence. Hosts may own user-facing orchestration, upstream identity exchange, deployment composition and product workflow, but communicate through explicit APIs/contracts. No host obtains implied authority to mutate Clearfolio persistence.

## Consequences

Clearfolio requires stable public/service interfaces and replaceable adapters. Some data may be duplicated at service boundaries, but ownership and rollback remain understandable. Central `.github` automation is a development control plane, not a runtime dependency.

## Failure and recovery

If a host integration is unavailable, standalone Clearfolio behavior remains testable and deployable. Host-specific failures must not corrupt Clearfolio state. Integration retries and idempotency are contract concerns rather than cross-database repair.

## Security and privacy

Tenant/actor authority must cross the service boundary through explicit trusted claims or tokens with least privilege. Hosts do not bypass Clearfolio tenant checks. Data minimization applies to integration payloads.

## Compatibility and migration

Existing HTTP flows remain valid **except where an accepted security contract explicitly requires a versioned tightening**. The direct conversion-job download route is such an exception: permission-only byte access is not the final contract and must converge on the signed artifact-delivery authority defined by `docs/API_CONTRACT.md` and ADR-0002. Existing direct-download clients therefore require an explicit migration rather than an assertion that their current request shape remains indefinitely valid.

Future naruon contracts are versioned and introduced additively or with explicit migration. A move to shared persistence would require a superseding ADR and a tenant/data-ownership migration plan.

## Tests and acceptance

- standalone application startup and API tests;
- versioned integration contract tests when naruon composition stabilizes;
- tenant authorization tests at the service boundary;
- direct-download consumer tests that prove the signed-delivery migration and controlled failure semantics;
- no cross-service application-DB dependency in deployment manifests.

## Rollback / supersession

Individual adapters may be rolled back without changing service ownership. Security-contract migration must not roll document-byte delivery back to permission-only access. Supersede this ADR only if product ownership itself changes and the new architecture documents data authority, migration and rollback.
