# ADR: Separate liveness and readiness probes

- Status: Accepted
- Date: 2026-08-05
- Decision owners: Clearfolio maintainers

## Context

Clearfolio previously exposed only `GET /healthz`. The implementation returned a
static success payload and described the endpoint as a liveness check, while the
README and architecture documents described the same route as readiness. That
ambiguity lets an orchestrator use a process-alive signal as a traffic-routing
signal.

Liveness and readiness answer different operational questions. Liveness asks
whether the process is irrecoverably unhealthy and should be restarted.
Readiness asks whether the current instance should receive traffic. A temporary
readiness failure must not cause a restart cascade.

## Decision

Clearfolio exposes two unauthenticated, non-cacheable probes on the main
application port:

| Route | Source of truth | Success | Unavailable |
| --- | --- | --- | --- |
| `GET /healthz` | Spring Boot `LivenessState` | `200 {"status":"ok"}` | `503 {"status":"broken"}` |
| `GET /readyz` | Spring Boot `ReadinessState` | `200 {"status":"ready"}` | `503 {"status":"not_ready"}` |

Both responses include `Cache-Control: no-store`. The existing successful
`/healthz` payload remains stable for backward compatibility.

The liveness probe must remain independent of shared external services such as a
database, object store, gateway, or model provider. Restarting an otherwise
recoverable application during an external outage can amplify the outage.
Readiness may later incorporate instance-local conditions that determine
whether this instance can safely accept traffic, such as completed startup
recovery or bounded-queue overload. Such changes must update this ADR and add
executable failure and recovery tests.

Spring Boot's `ApplicationAvailability` is the in-process source of truth. This
keeps probe semantics available without adding the Actuator dependency or a
second management port and preserves standalone deployment. The implementation
and reference documentation are version-aligned to Spring Boot 3.5.16, the
version managed by this repository.

## Kubernetes example

A startup probe protects slow or variable initialization from premature
liveness restarts. Kubernetes suppresses liveness and readiness checks until the
startup probe succeeds.

```yaml
startupProbe:
  httpGet:
    path: /healthz
    port: 8080
  periodSeconds: 5
  timeoutSeconds: 2
  failureThreshold: 24 # 120-second startup budget
livenessProbe:
  httpGet:
    path: /healthz
    port: 8080
  periodSeconds: 10
  timeoutSeconds: 2
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: /readyz
    port: 8080
  periodSeconds: 5
  timeoutSeconds: 2
  failureThreshold: 3
  successThreshold: 1
```

Probe timings are deployment inputs rather than application constants. Operators
must tune startup budgets, periods, timeouts, and failure thresholds against
measured startup, overload, and recovery behavior. The example deliberately
uses small dedicated response bodies because Kubernetes determines HTTP probe
success from the status code and recommends minimal health-check payloads.

## Security and privacy

- Probe responses disclose only a controlled state label.
- They contain no tenant, document, queue, dependency, credential, build, or
  exception details.
- They are intentionally unauthenticated so container orchestrators can call
  them, but they do not grant access to protected APIs.
- `Cache-Control: no-store` prevents intermediaries from replaying stale
  availability state.
- Each response remains far below Kubernetes' 10 KiB HTTP-probe body read limit.

## Verification contract

Automated tests must prove:

- `CORRECT` liveness returns `200` and `ok`;
- `BROKEN` liveness returns `503` and `broken`;
- `ACCEPTING_TRAFFIC` readiness returns `200` and `ready`;
- `REFUSING_TRAFFIC` readiness returns `503` and `not_ready`;
- every probe response is non-cacheable;
- controller construction fails without the availability provider;
- repository production line and branch coverage remains 100%.

The release gate also requires exact-head CI, Security Scan, SAST, fuzzing,
automated review, independent approval, and all repository protections.

## Consequences

### Positive

- Kubernetes and other orchestrators can distinguish startup completion,
  restart eligibility, and traffic eligibility.
- The `/healthz` success contract remains compatible with existing callers.
- Future readiness signals have an explicit, testable extension point.

### Trade-offs

- Operators must configure three probe roles across two routes.
- A static successful liveness response is no longer sufficient when Spring
  marks the application `BROKEN`.
- Readiness currently reflects Spring application state, not durable database,
  object-store, or queue health. Those dependencies remain separate commercial
  hardening slices.

## Rollback

A rollback may remove `/readyz` and restore the old `/healthz` implementation,
but deployment manifests must be rolled back at the same time. Do not point both
Kubernetes probes at `/healthz`, because doing so recreates the original semantic
ambiguity. Remove the startup probe only when measured startup behavior and the
replacement deployment policy provide an equivalent startup-failure budget.

## References

Broadcom, Inc. (n.d.). *SpringApplication: Application availability (Spring Boot
3.5.16)*. Spring. Retrieved August 5, 2026, from
https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.application-availability

The Kubernetes Authors. (2026, April 17). *Liveness, readiness, and startup
probes*. Kubernetes.
https://kubernetes.io/docs/concepts/workloads/pods/probes/
