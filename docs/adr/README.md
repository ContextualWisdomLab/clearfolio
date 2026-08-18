# Clearfolio Architecture Decision Records

Status: Canonical ADR index
Baseline: protected `main` at `83ec6f7fe2b04bdcd28bf98ec350e41e55730a18`

ADR status and implementation maturity are intentionally separate. `Accepted` means the architecture decision is authoritative; it does not imply that every consequence is implemented on protected main. Each ADR therefore records implementation status explicitly.

| ADR | Decision | Status | Implementation maturity |
| --- | --- | --- | --- |
| [0001](0001-standalone-msa-ownership.md) | Clearfolio remains independently deployable with explicit host/MSA authority boundaries | Accepted | `PARTIAL` / `ACCEPTED_ARCHITECTURE` |
| [0002](0002-tenant-artifact-authorization.md) | Tenant authorization and signed artifact delivery are separate required controls | Accepted | `IMPLEMENTED_ON_MAIN` + `ACTIVE_PR` direct-download alignment |
| [0003](0003-audit-pseudonymization-key-separation.md) | Audit pseudonyms use purpose-separated, domain-separated cryptographic material | Proposed | `ACTIVE_PR` #270/#268 |
| [0004](0004-durable-lifecycle-generation-fencing.md) | Durable job/artifact deletion uses immutable identity, generation fencing and restart-safe evidence | Proposed | `ACTIVE_PR` #268 |
| [0005](0005-deterministic-conversion-fidelity.md) | Supported-format claims require deterministic real conversion and fidelity evidence; placeholders are non-production | Accepted | `PARTIAL`; real Office conversion `PLANNED` |
| [0006](0006-liveness-readiness-separation.md) | Process liveness and traffic readiness are independent signals | Proposed | `ACTIVE_PR` #295 |
| [0007](0007-exact-head-live-base-evidence.md) | Merge/release evidence binds exact source head and independently resolved live base | Proposed | CI semantics `ACTIVE_PR` #270; operational policy accepted |
| [0008](0008-central-vs-local-automation-authority.md) | Central PR maintenance and leaf product development have separate authority and credentials | Proposed | `.github` dependency + `ACTIVE_PR` #271 |
| [0009](0009-work-conserving-rca-loop.md) | Automation uses work-conserving RCA→remedies→feasibility→action→proof and cannot stop after one item | Proposed | scheduler/prompt policy in progress |
| [0010](0010-release-provenance-fidelity-gate.md) | Release requires integrated security, fidelity, accessibility, provenance and operability evidence | Accepted | `PARTIAL` |
| [0011](0011-thin-scheduler-control-plane.md) | Recurring scheduler stays a thin execution control plane while reviewed repository documents own detailed product authority | Accepted | external scheduler applied; repository-local automation remains `ACTIVE_PR` #271 |
| [0012](0012-scheduler-execution-receipts.md) | Scheduler execution receipt, failure envelope, and budget continuation semantics make autonomous runs diagnosable and resumable without inventing hidden causes | Proposed | `PLANNED`; issue #331 |

## Required ADR structure

Every material ADR contains:

- context and decision drivers;
- considered alternatives;
- decision;
- consequences and trade-offs;
- failure and recovery behavior;
- security/privacy/governance impact;
- compatibility/migration implications;
- tests/acceptance evidence;
- rollback or supersession criteria;
- implementation maturity.

## Supersession rule

Do not edit a historical decision to pretend a different decision was always in force. Materially changed decisions receive a new ADR that marks the old record superseded and explains migration and rollback.
