# ADR-0006: Separate process liveness from traffic readiness

Status: Proposed
Implementation maturity: `ACTIVE_PR` #295

## Context and drivers

An orchestrator needs two different answers: whether the Clearfolio process should be restarted and whether this instance can safely receive user traffic. Combining these signals can send traffic to an unready process or create restart cascades when a shared dependency is unhealthy.

## Alternatives

1. Keep one generic health endpoint for all conditions.
2. Make liveness depend on all shared dependencies.
3. Expose controlled liveness and readiness separately; keep readiness contributors instance-local and deterministic.

## Decision

Choose alternative 3. `/healthz` represents process liveness and `/readyz` represents traffic readiness. Responses expose only controlled labels, use `Cache-Control: no-store`, and do not disclose tenant, topology, credentials, queue details, dependency names, build identifiers or exception text.

### Probe access boundary

The endpoints are unauthenticated **only on the orchestration probe path**:

- HTTP method: `GET` only; mutation methods are not probe contracts;
- allowed network sources: same-instance/loopback health tooling and the deployment platform's explicitly configured node/orchestrator probe source set;
- Kubernetes/container deployments must route those sources over the internal workload/control-plane path and apply NetworkPolicy, security-group, service-mesh or equivalent controls where the platform supports source restriction;
- the public/user application ingress must not publish `/healthz` or `/readyz`; ingress/gateway route tests must prove they are absent from the public route table;
- if a deployment cannot keep a probe endpoint off a broader network path, that broader path requires the deployment's normal service authentication/authorization rather than inheriting the unauthenticated probe exception;
- no query parameter or request header enables diagnostic expansion.

A deployment manifest is non-compliant if an Internet/public ingress can reach either unauthenticated probe merely because the application controller exposes it.

## Consequences

Deployment manifests and runbooks must configure two probes plus their network boundary. Operational semantics become clearer and dependency incidents are less likely to amplify through unnecessary restarts.

## Failure and recovery

Liveness failure makes restart eligibility visible. Readiness failure removes an instance from routing without asserting the process is dead. Recovery returns readiness after the deterministic contributor recovers.

## Security and privacy

Probe endpoints remain low-information and never become diagnostic-detail endpoints. The unauthenticated exception is limited to the controlled probe sources above; it is not a general anonymous HTTP API policy.

## Compatibility and migration

Existing `/healthz` clients may currently interpret it as a generic/readiness signal, so the meaning must not be switched in place before consumers move.

Rollout order is mandatory:

1. deploy `/readyz` while preserving the pre-migration `/healthz` behavior expected by existing clients;
2. deploy internal-only probe routing/network policy for both endpoints and verify public ingress cannot reach them anonymously;
3. migrate orchestrator readiness probes and every known readiness consumer to `/readyz` while compatibility tests exercise both endpoints;
4. confirm no supported readiness consumer still depends on old `/healthz` readiness semantics;
5. only then switch `/healthz` to the process-liveness contract or retire any obsolete readiness behavior under an explicit compatibility note.

Before step 5, rollback restores the previous application/manifests and keeps the old `/healthz` semantics so migrated and non-migrated clients can be recovered safely. After step 5 is accepted on protected main, rollback must restore a release whose endpoint semantics and client manifests are a compatible pair; independently reverting only the server or only the probes is prohibited.

## Tests and acceptance

- startup, broken/recovered liveness and not-ready/ready transitions;
- `GET` probe behavior and rejection/absence of mutation routes;
- cache headers and no diagnostic leakage;
- deployment-contract tests proving only loopback/platform probe sources have anonymous network reachability;
- ingress/gateway tests proving neither probe is anonymously exposed on the public application ingress;
- compatibility tests covering old `/healthz` behavior plus new `/readyz` during migration;
- migration tests proving readiness consumers move before `/healthz` semantics change;
- rollback tests that restore a mutually compatible server/probe pair;
- exact-head CI/security gates.

## Rollback / supersession

Rollback may restore the previous compatible deployment while investigating an availability regression, but a successor design must preserve the semantic distinction and the probe-network boundary rather than call dependency readiness process liveness or expose diagnostics anonymously.
