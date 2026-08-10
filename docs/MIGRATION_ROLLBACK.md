# Clearfolio Migration, Rollback, and Recovery Contract

Status: Canonical operational-change authority
Baseline: protected `main` at `f3cc09a9838f0f88c81a2ceae22138fab80a2edb`

This document defines how Clearfolio changes runtime state, persistence formats, security contracts, converters, and deployment behavior without turning rollback into an improvised incident procedure.

## Current persistence truth

Protected main is not a database-backed production architecture yet.

- Conversion-job state is process-local/in-memory and is therefore lost on process restart.
- Converted artifacts can use the file-backed artifact store and survive restart on the same durable volume.
- Selected audit/snapshot evidence uses append-only file-ledger mechanisms where configured.
- Database/object-store distributed durability remains `PLANNED` or `ACTIVE_PR` depending on the bounded feature.

A conceptual ERD in `docs/DATA_MODEL.md` does not imply that every entity is persisted on protected main.

## Change classes

Every release must identify which classes it changes:

1. **stateless application change** — code/API behavior without durable-format changes;
2. **configuration/security change** — secrets, permissions, validation, feature flags or policy;
3. **file-format/ledger change** — artifact metadata or append-only ledger encoding/replay semantics;
4. **converter/runtime change** — document renderer, fonts, PDF/runtime dependency or sandbox profile;
5. **persistence-schema change** — future SQL/object-store metadata schema;
6. **public-contract change** — API payload, status, token, integration, failure or compatibility semantics.

The rollback strategy must match the class instead of assuming that restoring an old application binary restores durable state safely.

## Pre-deployment gate

Before a state-affecting release reaches production, capture:

- exact release/source/artifact digest and dependency/SBOM provenance;
- changed durable formats and compatibility direction;
- migration command or deterministic startup migration, if any;
- backup/snapshot requirements and restore rehearsal evidence;
- downgrade compatibility statement;
- rollback trigger and maximum safe rollback point;
- affected API/client versions;
- tenant/security/data-retention impact;
- operational signals that distinguish bad rollout from external dependency failure.

A migration without a tested restore/rollback or explicit irreversible-change acceptance is not release-ready.

## File-ledger compatibility

Append-only ledger readers must fail closed on malformed or semantically impossible records rather than silently discarding valid history. A format evolution must provide one of:

- backward-compatible reader support;
- an explicit offline/atomic rewrite with integrity verification and backup;
- a versioned new ledger path with controlled cutover.

Recovery evidence must cover clean replay, truncated/torn tail handling where the format supports it, invalid UTF-8/record rejection, duplicate/replayed records, and restart after an interrupted write.

Old binaries must not be reintroduced after they can no longer interpret the current ledger safely.

## Artifact-store rollback

Artifact bytes and metadata are security-sensitive durable state.

- Never roll back to code that interprets a current artifact as belonging to another tenant/generation.
- Preserve checksum/integrity validation across rollback.
- Deletion/retention operations that have crossed an irreversible boundary require recovery/compensation, not resurrection from stale metadata.
- If a release changes on-disk layout, migrate by copy/verify/atomic switch where practical rather than in-place destructive mutation.
- Restore exercises must prove that signed-delivery and tenant-ownership checks still protect recovered artifacts.

Active PR #268 adds stronger lifecycle/deletion fencing. It remains `ACTIVE_PR`, not protected-main recovery behavior.

## Converter/runtime rollback

A converter or PDF/runtime upgrade can change rendered semantics even if APIs remain compatible. Rollback decisions therefore require the fidelity evidence in `docs/FIDELITY_ACCEPTANCE.md`.

For each converter/runtime change:

- preserve the previous pinned version and its evidence until the new release is accepted;
- compare realistic fixtures before rollout;
- record converter/font/configuration provenance;
- treat output-semantic regression as a release rollback trigger;
- do not preserve an insecure old converter solely for byte-for-byte compatibility.

Security fixes can intentionally break backward compatibility when continuing old behavior is unsafe; the migration note must explain the client/operational impact.

## Public API and token migration

Breaking public changes require a versioned migration or an explicit security-tightening exception with client updates.

Artifact-token interpretation changes must define:

- version recognition;
- issue/accept overlap period where safe;
- expiration/revocation behavior for old tokens;
- rollback behavior when old code cannot safely validate newly issued tokens.

Rollback must not re-enable a known authorization bypass simply to accept legacy requests.

## Future SQL persistence

When SQL job persistence is implemented, migrations must additionally prove:

- descriptive two-or-more-word `snake_case` object naming except externally mandated names;
- forward and rollback/recovery paths for every schema change;
- transactional boundaries and lock/runtime bounds;
- concurrent old/new application compatibility for rolling deployment where claimed;
- uniqueness/tenant/generation invariants in the database, not only application memory;
- backup/restore and point-in-time recovery evidence appropriate to the deployment profile;
- migration failure does not produce a partially authorized or partially upgraded serving state.

The database must remain service-owned; naruon and other hosts integrate through versioned Clearfolio interfaces rather than cross-service table access.

## Deployment rollback sequence

A normal rollback sequence is:

1. stop or drain new traffic where the failure can corrupt/complicate state;
2. capture failure evidence and exact deployed revision;
3. determine whether durable state crossed a non-backward-compatible boundary;
4. if safe, deploy the last proven compatible artifact/configuration;
5. if not safe, execute the tested data/ledger restore or forward-fix path;
6. revalidate liveness/readiness, tenant isolation, signed artifact delivery, conversion lifecycle, and representative buyer flow;
7. preserve incident evidence and update the migration/ADR/release record.

Rollback completion is an operational proof, not merely “the old pod started.”

## Recovery acceptance

A recovery-capable release has deterministic tests or rehearsals for the states it claims to survive, including as applicable:

- process crash/restart;
- torn or invalid file-ledger tail;
- missing/corrupt artifact metadata;
- cleanup/deletion retry;
- stale in-flight work;
- failed configuration/secret rotation;
- previous/new converter version transition;
- future SQL migration interruption;
- restore to a clean environment followed by buyer-critical smoke tests.

## Traceability

- Domain/persistence ownership: `docs/DATA_MODEL.md`
- Operational degraded/recovery behavior: `docs/OPERABILITY.md`
- Fidelity rollback gate: `docs/FIDELITY_ACCEPTANCE.md`
- Release acceptance: `docs/RELEASE_ACCEPTANCE.md`
- Security threats: `docs/THREAT_MODEL.md`
- Requirements/evidence: `docs/TRACEABILITY.md`
