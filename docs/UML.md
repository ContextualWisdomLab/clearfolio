# Clearfolio UML and Architecture Diagrams

Status: Canonical diagram index
Baseline: protected `main` at `83ec6f7fe2b04bdcd28bf98ec350e41e55730a18`

Diagrams are architecture views, not deployment evidence. `ACTIVE_PR` labels identify behavior that is not yet protected-main functionality.

## 1. Bounded-context / component view

```mermaid
flowchart LR
    Client[Document user / host client]
    Operator[Tenant operator]
    Gateway[Trusted identity / gateway boundary]

    subgraph Clearfolio[Clearfolio service]
        HTTP[WebFlux HTTP controllers]
        Auth[Tenant access boundary]
        Convert[Conversion orchestration]
        Worker[Bounded conversion worker]
        Repo[Job repository / state store]
        Artifact[Artifact store]
        Link[Signed artifact link service]
        Ledger[Audit / artifact link ledgers]
        Viewer[Viewer shell / PDF.js]
        Availability[Health / availability]
    end

    Client --> HTTP
    Operator --> HTTP
    Gateway --> Auth
    HTTP --> Auth
    HTTP --> Convert
    Convert --> Repo
    Convert --> Worker
    Worker --> Repo
    Worker --> Artifact
    HTTP --> Link
    Link --> Artifact
    Link --> Ledger
    HTTP --> Viewer
    HTTP --> Availability

    Naruon[naruon / other CWL host] -. versioned API contract .-> HTTP
    Central[ContextualWisdomLab/.github] -. development control plane only .-> Clearfolio
```

## 2. Submit → convert → view sequence

```mermaid
sequenceDiagram
    actor User
    participant API as ConversionController
    participant Auth as TenantAccessService
    participant Service as DocumentConversionService
    participant Repo as ConversionJobRepository
    participant Worker as DefaultConversionWorker
    participant Store as ArtifactStore
    participant Links as ArtifactLinkService
    participant Viewer as Viewer/PDF.js

    User->>API: POST /api/v1/convert/jobs + tenant claims + file
    API->>Auth: require job:create
    Auth-->>API: tenant_context
    API->>Service: submit(file, policy, tenant_context)
    Service->>Service: validate + content identity + dedupe
    Service->>Repo: save conversion_job
    Service->>Worker: enqueue(job)
    API-->>User: 202 + jobId + statusUrl
    Worker->>Store: publish PDF artifact
    Worker->>Repo: mark SUCCEEDED
    User->>API: GET viewer bootstrap
    API->>Auth: require viewer:read + same tenant
    API->>Links: createLink(job, tenant_context)
    Links->>Store: checksum current artifact
    Links-->>API: signed artifact URL
    API-->>Viewer: bootstrap metadata
```

For non-PDF inputs on protected main, artifact generation may be development placeholder behavior; this sequence does not imply Office-fidelity acceptance.

## 3. Tenant-authorized signed artifact read

```mermaid
sequenceDiagram
    actor Caller
    participant API as Artifact/download endpoint
    participant Auth as TenantAccessService
    participant Repo as Job repository
    participant Store as ArtifactStore
    participant Token as ArtifactLinkService
    participant Ledger as ArtifactLinkLedger

    Caller->>API: request bytes + tenant claims + signed artifact token
    API->>Auth: require artifact:read where endpoint policy requires
    Auth-->>API: tenant_context
    API->>Repo: get tenant-owned conversion_job
    API->>Store: get artifact bytes
    API->>Token: verifyReadToken(job, bytes, token)
    Token->>Ledger: verify issuance + not revoked
    Token->>Token: expiry/scope/doc/tenant/checksum checks
    Token-->>API: verified claims
    API->>API: resolve zero or one Range
    API->>Ledger: record controlled read evidence
    API-->>Caller: 200 / 206 / 416 or controlled auth failure
```

Protected-main `ArtifactController` already follows this authority pattern. Direct conversion-job download alignment is an `ACTIVE_PR` security remediation and must not be described as complete until integrated.

## 4. Conversion job state machine

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED
    SUBMITTED --> PROCESSING: worker claim
    PROCESSING --> SUCCEEDED: artifact published
    PROCESSING --> SUBMITTED: bounded retry scheduled / recovery
    PROCESSING --> FAILED: retry exhausted or terminal failure
    FAILED --> SUBMITTED: authorized dead-letter retry
    SUCCEEDED --> [*]
    FAILED --> [*]: terminal until authorized retry/delete
```

Dead-letter exhaustion is represented by `FAILED` plus dead-letter evidence rather than a separate public lifecycle enum.

## 5. Durable deletion recovery (`ACTIVE_PR` #268)

```mermaid
sequenceDiagram
    actor Operator
    participant API as Admin/delete boundary
    participant Auth as TenantAccessService
    participant Receipt as Deletion receipt store
    participant Artifact as Lifecycle-fenced artifact store
    participant Repo as Job repository
    participant Recovery as Recovery coordinator

    Operator->>API: delete job
    API->>Auth: require signed tenant + admin/write authority
    API->>Receipt: persist pending deletion receipt first
    API->>Artifact: read/bind exact generation + checksum
    alt artifact readable
        Artifact-->>API: bytes/checksum or confirmed absence
        API->>Receipt: bind verified digest state
        API->>Repo: tombstone exact tenant/job/generation
        API->>Artifact: cleanup exact generation
        API->>Receipt: mark cleanup completion
    else artifact read fails
        API->>Receipt: append controlled failed attempt
        API-->>Operator: controlled retryable failure
        Recovery->>Receipt: rebuild next eligible order after restart
    end
```

This is target behavior from the active PR, not protected-main evidence.

## 6. Liveness/readiness flow (`ACTIVE_PR` #295)

```mermaid
flowchart TD
    K[Kubernetes / orchestrator]
    L[/healthz liveness/]
    R[/readyz readiness/]
    Process[Process health]
    Traffic[Instance accepts traffic]

    K --> L --> Process
    K --> R --> Traffic
    Process -->|broken| Restart[restart candidate]
    Traffic -->|not ready| Remove[remove from routing]
    Traffic -->|ready| Route[route traffic]
```

Shared dependency health must not create restart cascades by contaminating process liveness.

## 7. Office conversion adapter isolation (`ACTIVE_PR` #306 / `PLANNED` issue #5)

PR #306 defines the provider-neutral `OfficeConversionAdapter` contract and deterministic publication-validation boundary as `ACTIVE_PR` evidence. The production Office runtime remains `PLANNED`: a `sandboxed_office_sidecar` or independently operated `remote_office_service` must own Office-process execution **outside the API container**. The `deterministic_fixture_adapter` is an `ACTIVE_PR` offline contract oracle, not a production Office engine. In-process `LocalOfficeManager` inside the Clearfolio API container remains rejected.

```mermaid
sequenceDiagram
    actor Caller
    participant API as Clearfolio API container
    participant Preflight as Source preflight / policy
    participant Adapter as OfficeConversionAdapter - ACTIVE_PR #306
    participant Fixture as deterministic_fixture_adapter - ACTIVE_PR oracle
    participant Sidecar as sandboxed_office_sidecar - PLANNED
    participant Remote as remote_office_service - PLANNED
    participant Publish as PDF publication validator
    participant Store as ArtifactStore

    Caller->>API: non-PDF document + tenant/job/generation contract
    API->>Preflight: validate source format/container/size/active-content policy
    Preflight-->>API: accepted qualified request or fail-closed rejection
    API->>Adapter: immutable conversion request
    alt deterministic contract test only
        Adapter->>Fixture: convert bounded fixture
        Fixture-->>Adapter: deterministic PDF result + provenance
    else sandboxed production candidate after qualification
        Adapter->>Sidecar: authenticated bounded conversion request
        Sidecar-->>Adapter: PDF result + engine/runtime evidence
    else remote production candidate after qualification
        Adapter->>Remote: authenticated bounded conversion request
        Remote-->>Adapter: PDF result + engine/runtime evidence
    end
    Adapter-->>API: provider-neutral result
    API->>Publish: validate request/result binding, PDF structure, size/pages, active-action policy
    Publish-->>API: accepted artifact or typed failure
    API->>Store: publish only accepted output
```

The sidecar and remote providers are alternatives, not simultaneous requirements. Their qualification must prove isolation, no inherited application secrets, deny-by-default outbound networking, bounded CPU/RAM/disk/process lifetime, cancellation/restart/cleanup, exact runtime/license/SBOM/provenance, hostile-source handling, and realistic Office fidelity. Neither this diagram nor a successful adapter invocation establishes production Office support; support requires issue #5 release evidence and protected-main integration.

## 8. Automation authority flow

```mermaid
flowchart LR
    Central[Central .github PR maintenance]
    Review[Review / repair / exact-head revalidation]
    Merge[Protected merge authority]

    Local[Clearfolio product-development loop - ACTIVE_PR]
    RCA[RCA + remedies + feasibility]
    Proposal[Bounded path-disjoint Draft proposal]
    Verify[Credential-free full verification]

    Central --> Review --> Merge
    Local --> RCA --> Proposal --> Verify
    Verify -. never self-approve/merge .-> Review
```

The central control plane and leaf product-development agent are intentionally separate. A local scheduler must not copy central reviewer/merge credentials.

## 9. Standalone and MSA deployment topology

```mermaid
flowchart TB
    subgraph Standalone[Standalone deployment]
        Browser1[Browser / API client] --> CF1[Clearfolio]
        CF1 --> LocalState[Configured local state/artifacts]
    end

    subgraph Composed[MSA composition]
        Browser2[User client] --> Host[naruon / host service]
        Host -->|versioned Clearfolio API| CF2[Clearfolio]
        Identity[Host identity / gateway] -->|signed/scoped claims| CF2
        CF2 --> State[Clearfolio-owned state/artifacts]
        Orchestrator[contextual-orchestrator] -. optional model routing outside deterministic conversion authority .-> Host
    end

    CentralCI[ContextualWisdomLab/.github] -. CI/review control plane .-> CF1
    CentralCI -. CI/review control plane .-> CF2
```

No composition arrow authorizes direct host writes to Clearfolio application persistence.

## 10. Degraded/failure modes

```mermaid
flowchart TD
    Input[Request / background work] --> Gate{Boundary result}
    Gate -->|unauthorized / cross-tenant| Conceal[controlled 401/403/404]
    Gate -->|unsupported / malformed / oversized| Reject[controlled fail-closed rejection]
    Gate -->|queue saturated| Backpressure[bounded refusal / planned durable backpressure]
    Gate -->|conversion terminal failure| Failed[FAILED + retry/dead-letter evidence]
    Gate -->|artifact unavailable| NoArtifact[controlled not-found/not-ready]
    Gate -->|valid| Continue[continue workflow]
    Failed --> Recovery[authorized retry / restart recovery]
```

## 11. Scheduler execution receipt and resumable continuation (`PLANNED`; issue #331)

This diagram models the contract from ADR-0012. It does not claim that the external scheduler platform already stores every receipt or that Draft PR #271 is protected-main behavior.

```mermaid
sequenceDiagram
    participant Clock as Schedule
    participant Control as External scheduler / admission
    participant GitHub as Live GitHub authority
    participant Queue as Fresh queue
    participant Writer as Single Clearfolio writer
    participant Receipt as External action receipt store

    Clock->>Control: schedule recurrence
    Control->>Control: admission + run identity
    Control->>GitHub: refetch main, PRs/issues, reviews/checks, blobs/refs, writers
    GitHub-->>Control: exact live evidence
    Control->>Queue: construct fresh queue
    Queue-->>Control: next bounded atomic action
    Control->>Writer: execute exact-head/blob/ref-bound action
    alt action succeeds at stable boundary
        Writer-->>Receipt: action receipt + exact proof
        Receipt-->>Control: next action permitted
        Control->>GitHub: refetch affected state
    else practical budget nearly exhausted
        Writer-->>Receipt: last safe checkpoint
        Receipt-->>Control: budget continuation
        Note over Control,GitHub: Next run must rebuild a fresh queue; checkpoint SHAs are historical
    else observed execution failure
        Writer-->>Receipt: controlled failure envelope + last safe checkpoint
        Receipt-->>Control: continuation handoff or branch-local defer
        Note over Control,Receipt: Generic scheduled-task error remains a symptom; do not invent hidden cause
    end
```

### Receipt state machine

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED: schedule
    SCHEDULED --> ADMITTED: admission
    ADMITTED --> QUEUE_BUILT: fresh queue
    QUEUE_BUILT --> ACTION_RUNNING: atomic action
    ACTION_RUNNING --> ACTION_PROVEN: action receipt
    ACTION_PROVEN --> QUEUE_BUILT: next safe item
    ACTION_PROVEN --> COMPLETED: two fresh exit sweeps
    ACTION_RUNNING --> BUDGET_CONTINUATION: verified safe checkpoint
    ACTION_RUNNING --> FAILURE_ENVELOPE: observed controlled failure
    BUDGET_CONTINUATION --> [*]: continuation handoff
    FAILURE_ENVELOPE --> [*]: continuation handoff / local defer
    COMPLETED --> [*]
```

`BUDGET_CONTINUATION` is not product completion. `FAILURE_ENVELOPE` is not a source-code finding or a guessed root cause. The next invocation independently resolves every live GitHub identity before another mutation.

## Related detailed diagrams

Existing dated/feature diagrams remain useful detailed views:

- `docs/diagrams/submit-flow.md`
- `docs/diagrams/submit-policy-adapter-flow.md`
- `docs/diagrams/status-flow.md`
- `docs/diagrams/preview-flow.md`
- `docs/diagrams/retry-deadletter-flow.md`

When a detailed diagram conflicts with this canonical index or protected code, it must be updated or explicitly marked historical.
