# Liveness and readiness probe operations

Date: 2026-08-05
Maturity: `ACTIVE_PR` until this exact implementation integrates
Canonical decision owner: ADR-0006 in the canonical documentation line

## Operational problem

Clearfolio previously exposed only `GET /healthz`. The implementation returned a
static success payload and described the endpoint as a liveness check, while
some deployment prose used the same route as a traffic-readiness signal. That
ambiguity lets an orchestrator route requests to a live-but-unready instance or
restart a recoverable process during a temporary dependency condition.

Liveness and readiness answer different questions:

- liveness asks whether the process is irrecoverably unhealthy and should be
  restarted;
- readiness asks whether the current instance should receive traffic.

A temporary readiness failure must not create a restart cascade.

## Route contract

This implementation exposes two unauthenticated, non-cacheable probes on the
main application port:

| Route | Source of truth | Success | Unavailable |
| --- | --- | --- | --- |
| `GET /healthz` | Spring Boot `LivenessState` | `200 {"status":"ok"}` | `503 {"status":"broken"}` |
| `GET /readyz` | Spring Boot `ReadinessState` | `200 {"status":"ready"}` | `503 {"status":"not_ready"}` |

Both responses include `Cache-Control: no-store`. The successful `/healthz`
payload remains stable for existing callers.

The liveness probe remains independent of shared external services such as a
database, object store, gateway, or model provider. Readiness may later include
instance-local conditions that determine whether this instance can safely
accept traffic, such as completed startup recovery or bounded-queue overload.
Any extension must update the canonical availability decision and add
executable failure and recovery tests.

Spring Boot `ApplicationAvailability` is the in-process source of truth. The
controller fails construction when that provider is absent rather than
inventing healthy state.

## Kubernetes example

A startup probe protects variable initialization from premature liveness
restarts. Kubernetes suppresses liveness and readiness checks until the startup
probe succeeds.

```yaml
startupProbe:
  httpGet:
    path: /healthz
    port: 8080
  periodSeconds: 5
  timeoutSeconds: 2
  failureThreshold: 24
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

Probe timings are deployment inputs rather than application constants.
Operators tune startup budgets, periods, timeouts, and failure thresholds
against measured startup, overload, and recovery behavior.

## Security and privacy

- Probe responses disclose only one controlled state label.
- They contain no tenant, document, queue, dependency, credential, build, or
  exception detail.
- They are intentionally unauthenticated so orchestrators can call them, but
  they grant no access to protected APIs.
- `Cache-Control: no-store` prevents stale availability replay.
- Responses remain deliberately small.

## Verification contract

Exact-head tests prove:

- `CORRECT` liveness returns `200` and `ok`;
- `BROKEN` liveness returns `503` and `broken`;
- `ACCEPTING_TRAFFIC` readiness returns `200` and `ready`;
- `REFUSING_TRAFFIC` readiness returns `503` and `not_ready`;
- every probe response is non-cacheable;
- controller construction fails without the availability provider;
- production line and branch coverage remains 100%.

Release and merge still require current exact-head CI, Security Scan, SAST,
fuzzing, zero valid unresolved findings, qualifying independent approval, and
all repository protections. A source-head success on an older base is not
current integration evidence.

## Rollback

A rollback may remove `/readyz` and restore the old `/healthz` implementation,
but deployment manifests must be rolled back at the same time. Do not point
both Kubernetes probes at `/healthz`, because doing so recreates the original
semantic ambiguity. Remove the startup probe only when measured startup
behavior and the replacement deployment policy provide an equivalent
startup-failure budget.

## References

Broadcom, Inc. (n.d.). *SpringApplication: Application availability (Spring Boot
3.5.16)*. Spring. Retrieved August 5, 2026, from
https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.application-availability

The Kubernetes Authors. (2026, April 17). *Liveness, readiness, and startup
probes*. Kubernetes.
https://kubernetes.io/docs/concepts/workloads/pods/probes/
