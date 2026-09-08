# Product–technical gap baseline

## Scope

This baseline records buyer-visible and control-plane gaps that are demonstrated by the current Clearfolio code and pull-request evidence. It is not a release claim.

Protected authority reviewed for this lane: `main@06633a25109c62e24a7015ae04fb9f6e0a246f7e`. The admin authorization repair is tracked in PR #563; production/test code evidence was repaired through `b058809a5ddf4a6609f5c0071865da0da1e4d87c` before this documentation descendant.

## Admin conversion-job authorization

### Problem

`/api/v1/admin/convert/jobs` exposes tenant-scoped operational data and mutations. The boundary must enforce the request tenant, authenticated subject, endpoint-specific permission, and object tenant before data is returned or state changes are accepted.

### Current candidate contract

- list: requires `job:read` and returns only jobs belonging to the authenticated tenant;
- delete: requires `job:delete` and delegates deletion with the verified `TenantContext`, returning 404 when the scoped job is unavailable;
- retry: requires `job:retry`, hides cross-tenant jobs as 404, uses the authenticated subject for the retry transition, maps post-lookup disappearance to 404, and maps ineligible state to 409;
- authentication/permission regressions use the real `TenantAccessService` boundary rather than a permissive mock, so missing claims produce 401 and wrong endpoint permissions produce 403.

The post-lookup retry disappearance regression is a correctness repair, not proof that lookup and transition are atomic. If stronger invariants require eliminating the lookup/transition window rather than accurately mapping its result, that state transition belongs in the conversion domain service/repository transaction boundary.

### RED / GREEN acceptance

RED evidence is any current-head path where missing claims are accepted, the wrong permission can call an admin operation, a different tenant's job is returned or retried, or `RetryDeadLetterResult.NOT_FOUND` is reported as 202.

GREEN requires all of the following on one unchanged exact PR head:

- controller and tenant-access tests cover 401, 403, tenant filtering/404, ACCEPTED, NOT_FOUND, and NOT_ELIGIBLE branches;
- JaCoCo line and branch thresholds remain at repository policy;
- required CI, Security Scan, SAST, CodeQL and applicable fuzz evidence are terminal-valid for that exact head;
- current review threads contain no unresolved actionable finding;
- no scanner suppression, self-approval, predecessor receipt, or merge-policy weakening substitutes for evidence.

## DDD boundary

`TenantAccessService` is an authorization-policy boundary. It may authenticate request claims and enforce endpoint permission/tenant visibility, but conversion-job lifecycle truth remains in `DocumentConversionService` and its repository. Cross-tenant filtering or authorization must not be reimplemented through cross-service SQL or a mutable sibling repository dependency.

A future atomic retry transition, if required, should accept the verified tenant/subject authorization context or equivalent immutable values and perform ownership + retry eligibility + state change in the owning conversion aggregate transaction. The controller should map the returned domain result; it should not become the aggregate owner.

## Standard traceability

OWASP ASVS 5.0.0 is the current stable ASVS release. This lane maps primarily to:

- `v5.0.0-8.1.1`: authorization rules should document function- and data-specific restrictions;
- `v5.0.0-8.2.1`: function-level access is restricted to explicitly permitted consumers;
- `v5.0.0-8.2.2`: data-specific access is restricted to permitted data items;
- `v5.0.0-8.3.1`: authorization is enforced at a trusted service layer;
- `v5.0.0-8.4.1`: multi-tenant applications enforce cross-tenant controls.

Reference: OWASP Foundation. (2025). *OWASP Application Security Verification Standard 5.0.0*. https://owasp.org/www-project-application-security-verification-standard/

## Release gap

PR #563 remains Draft until exact-head tests, coverage, security gates, and independent/current review evidence converge. An immutable release additionally requires the repository's version/CHANGELOG, artifact, SBOM/provenance, reproducibility, and rollback evidence from the accepted protected generation.