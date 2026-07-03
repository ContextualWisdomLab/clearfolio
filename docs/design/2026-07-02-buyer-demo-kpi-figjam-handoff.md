# Buyer Demo KPI FigJam Handoff

Date: 2026-07-02

## Figma Artifact

- FigJam: [Clearfolio Buyer Demo Evidence Flow](https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM?utm_source=codex&utm_content=edit_in_figjam&oai_id=&request_id=41b7cd77-c07e-475e-bd77-460b5911666c)
- Added FigJam diagram on the same board:
  `Clearfolio Threat Boundaries and Data Handling`.
- Added FigJam diagram on the same board:
  `Clearfolio License Diligence Closure Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Auth Tenant Boundary Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Operator Job Detail Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Runtime Tenant Enforcement Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Gateway Signed Tenant Claims Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Runtime Signed Artifact Link Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Artifact Revocation and Read Audit Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio File Backed Artifact Ledger Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio KPI Snapshot Evidence Ledger Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio KPI Snapshot Export Evidence API Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Buyer Demo KPI Evidence Panel Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Operator Recovery Evidence Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Buyer Integration Deployment Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Durable Job Repository Target Architecture`.
- Added FigJam diagram on the same board:
  `Clearfolio Conversion State Store Implementation Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Conversion Lifecycle Event Trail Flow`.
- Added FigJam diagram on the same board:
  `Clearfolio Seeded Buyer Demo Story Flow`.
- Added seeded buyer-demo screenshots on the same board:
  desktop node `25:1423`, mobile node `25:1422`.
- Added FigJam diagram on the same board:
  `Clearfolio KRW 2B Buyer Diligence Closure Map`.
- Added FigJam diagram on the same board:
  `Clearfolio Buyer Readiness Scorecard Gate Map`.
- Added FigJam diagram on the same board:
  `Clearfolio Buyer Diligence Slides Storyboard`.
- Added FigJam diagram on the same board:
  `Clearfolio Ready Gate Evidence Integrity Check`.
- Buyer diligence Slides handoff:
  `docs/design/2026-07-03-buyer-diligence-slides-and-closure-map.md`.
- Buyer diligence Slides generation payload:
  `docs/design/2026-07-03-buyer-diligence-slides-generation-payload.json`.
- Figma Code Connect: not used.

## Product Design Acceptance

- The first viewport must show the product name, upload action, and live KPI
  strip without requiring navigation.
- KPI labels must map to buyer-readable outcomes: runtime jobs, ready previews,
  conversion success rate, and p95 preview latency.
- Upload, status tracking, preview handoff, and evidence flow must remain visible
  as one buyer-demo journey rather than separate marketing pages.
- Session history rows must expose a readable job detail drawer before forcing
  buyers or operators into raw JSON.
- The UI must retain keyboard-accessible controls, visible focus, and live status
  announcements for upload and conversion state changes.
- KPI fallback behavior must not create contradictory buyer evidence: backend
  runtime metrics are primary, browser-session history is fallback only.
- KPI snapshot evidence ledger behavior must remain clearly labeled as local
  restart-replay evidence, not as the final durable analytics event store.
- KPI snapshot export lookup must remain tenant-scoped and should not expose
  raw source documents, converted artifacts, signed artifact tokens, or
  cross-tenant identifiers.
- The buyer-demo KPI evidence panel must show export count, latest export time,
  exporting subject, and runtime job count without requiring a raw JSON tab.
- The operator recovery evidence panel must stay scoped to the current browser
  session and should summarize retry posture without claiming production admin
  coverage.
- The seeded buyer-demo story must be labeled as local browser-session demo
  evidence, not production data or a durable analytics source.
- Desktop and mobile screenshot evidence must come from the running local
  product surface after clicking `Load demo story`, not from static mockups.
- Buyer diligence presentation artifacts must keep `Ready`, `Partial`, and gap
  states visually distinct; local seeded proof must not be styled as production
  data.

## Data Analytics Mapping

| UI KPI | API field | Buyer proof |
| --- | --- | --- |
| Runtime jobs | `totalJobs` | Shows observable conversion workload in the current runtime. |
| Ready | `succeededJobs` | Shows previewable documents available for buyer inspection. |
| Success rate | `conversionSuccessRate` | Shows conversion reliability as an acquisition diligence metric. |
| P95 preview | `p95TimeToPreviewMs` | Shows latency evidence for the demo path. |
| Snapshot export | `KpiSnapshotRecord` | Shows when a buyer-visible KPI snapshot was exported under tenant scope. |
| Snapshot evidence lookup | `KpiSnapshotExportResponse` | Lets an authorized buyer inspect exported KPI evidence without raw content. |
| KPI evidence panel | `/api/v1/analytics/kpi-snapshot-exports` | Turns export evidence into a buyer-readable UI panel while omitting tenant ids. |
| Recovery evidence panel | Browser session history plus job status payloads | Shows needs-action jobs, retry-ready dead letters, last accepted retry, and latest inspected detail without a new admin system. |
| Lifecycle event trail | `ConversionJobLifecycleEvent` | Proves ordered transition evidence in the current runtime without storing filenames, content hashes, artifact paths, signed tokens, or raw converter errors. |
| Seeded demo story | `demo-fixtures.json` | Gives screenshots, FigJam, and buyer-deck review one deterministic local story covering success, processing, unsupported-format, dead-letter, KPI snapshot, and KPI export evidence. |
| Buyer diligence closure map | FigJam diagram plus `docs/design/2026-07-03-buyer-diligence-slides-and-closure-map.md` | Aligns Product Design, Data Analytics, Figma, Superpowers, and Ponytail workstreams around current proof, open gaps, and next closure order. |
| Buyer readiness scorecard | `docs/diligence/2026-07-03-buyer-readiness-scorecard.md` plus FigJam scorecard gate map | Quantifies 23 data-room artifacts, 8 readiness gates, and 38 percent conservative gate readiness without hiding partial discount risks. |
| Buyer diligence Slides storyboard | `docs/design/2026-07-03-buyer-diligence-slides-generation-payload.json` plus FigJam storyboard | Makes the 11-slide buyer deck reproducible once Figma team or organization plan selection is available. |
| Ready gate evidence integrity | `scripts/check_buyer_dataroom_manifest.py` plus FigJam integrity check | Prevents a buyer-ready gate from citing partial or external artifacts as complete evidence. |

## Mermaid Source

### Buyer Demo Evidence Flow

```mermaid
flowchart LR
    buyer["Buyer reviewer"]

    subgraph demoSurface ["Buyer-demo surface"]
        root["GET /"]
        upload["Upload document"]
        history["Session history"]
        viewer["GET /viewer/{docId}"]
    end

    subgraph runtime ["Conversion runtime"]
        submit["POST /api/v1/convert/jobs"]
        repo[("Conversion jobs")]
        worker["Async conversion"]
        artifact["PDF artifact"]
        kpi["GET /api/v1/analytics/kpi-snapshot"]
    end

    subgraph diligence ["Diligence evidence"]
        plan["KRW 2B plan"]
        qa["Gate evidence"]
        figjam["FigJam flow"]
        pr["PR #74"]
    end

    buyer -->|"Opens"| root
    root -->|"Submits"| upload
    upload -->|"Calls"| submit
    submit -->|"Stores"| repo
    repo -->|"Feeds"| worker
    worker -->|"Creates"| artifact
    root -.->|"Reads"| kpi
    repo -->|"Counts"| kpi
    root -->|"Shows"| history
    history -->|"Launches"| viewer
    viewer -->|"Loads"| artifact
    kpi -->|"Proves"| qa
    plan -->|"Defines"| pr
    qa -->|"Supports"| pr
    figjam -->|"Explains"| pr

    style demoSurface fill:#C2E5FF,stroke:#3DADFF
    style runtime fill:#CDF4D3,stroke:#66D575
    style diligence fill:#FFECBD,stroke:#FFC943
    style qa fill:#DCCCFF,stroke:#874FFF
    style pr fill:#CDF4D3,stroke:#66D575
```

### Buyer Demo KPI Evidence Panel Flow

```mermaid
flowchart LR
    buyer["Buyer reviewer"]

    subgraph demoSurface ["Buyer-demo surface"]
        root["GET /"]
        kpiStrip["Live KPI strip"]
        evidencePanel["KPI snapshot evidence panel"]
        history["Session history"]
    end

    subgraph analyticsApi ["Analytics API"]
        snapshot["GET /api/v1/analytics/kpi-snapshot"]
        exports["GET /api/v1/analytics/kpi-snapshot-exports"]
    end

    subgraph evidenceStore ["Local evidence mode"]
        ledger[("KPI snapshot ledger")]
        record["Authorized snapshot record"]
    end

    subgraph buyerProof ["Buyer proof"]
        latest["Latest export time"]
        subject["Export subject"]
        jobs["Runtime job count"]
        noTenant["Tenant id omitted"]
    end

    buyer -->|"Opens"| root
    root -->|"Loads"| kpiStrip
    root -->|"Loads"| evidencePanel
    root -->|"Shows"| history
    kpiStrip -->|"Reads counters"| snapshot
    snapshot -->|"Records export"| ledger
    ledger -->|"Stores"| record
    evidencePanel -->|"Reads exports"| exports
    exports -->|"Filters by tenant"| ledger
    exports -->|"Returns evidence"| latest
    exports -->|"Returns evidence"| subject
    exports -->|"Returns evidence"| jobs
    exports -->|"Suppresses"| noTenant

    style demoSurface fill:#C2E5FF,stroke:#3DADFF
    style analyticsApi fill:#CDF4D3,stroke:#66D575
    style evidenceStore fill:#FFECBD,stroke:#FFC943
    style buyerProof fill:#DCCCFF,stroke:#874FFF
    style noTenant fill:#FFCDC2,stroke:#FF7556
```

### Seeded Buyer Demo Story Flow

```mermaid
flowchart LR
    fixture["demo-fixtures.json"]
    button["Load demo story"]
    storage[("Session history")]

    subgraph demoSurface ["Viewer demo surface"]
        history["History table"]
        kpiStrip["KPI strip"]
        evidencePanel["Evidence panel"]
        recoveryPanel["Recovery panel"]
        detailDrawer["Job detail"]
    end

    subgraph proofStates ["Buyer proof states"]
        successState["Succeeded job"]
        processingState["Processing job"]
        unsupportedState["Unsupported format"]
        deadLetterState["Dead letter"]
    end

    subgraph salesProof ["Sale proof outputs"]
        screenshots["Screenshots"]
        figjam["FigJam handoff"]
        buyerDeck["Buyer deck"]
    end

    fixture -->|"Loaded by"| button
    button -->|"Seeds"| storage
    storage -->|"Renders"| history
    fixture -->|"Feeds"| kpiStrip
    fixture -->|"Feeds"| evidencePanel
    history -->|"Summarizes"| recoveryPanel
    history -->|"Opens"| detailDrawer
    history -->|"Shows"| successState
    history -->|"Shows"| processingState
    history -->|"Shows"| unsupportedState
    history -->|"Shows"| deadLetterState
    kpiStrip -->|"Supports"| screenshots
    recoveryPanel -->|"Supports"| screenshots
    detailDrawer -->|"Supports"| screenshots
    screenshots -->|"Feeds"| figjam
    figjam -->|"Feeds"| buyerDeck

    style demoSurface fill:#C2E5FF,stroke:#3DADFF
    style proofStates fill:#FFECBD,stroke:#FFC943
    style salesProof fill:#CDF4D3,stroke:#66D575
    style unsupportedState fill:#FFE0C2,stroke:#FF9E42
    style deadLetterState fill:#FFCDC2,stroke:#FF7556
    style successState fill:#CDF4D3,stroke:#66D575
```

### Operator Recovery Evidence Flow

```mermaid
flowchart LR
    buyer["Buyer reviewer"]

    subgraph demoSurface ["Buyer-demo surface"]
        root["GET /"]
        recoveryPanel["Recovery evidence panel"]
        history["Session history"]
        detailDrawer["Job detail drawer"]
        retryButton["Retry action"]
    end

    subgraph statusApi ["Status API"]
        statusEndpoint["GET job status"]
        retryEndpoint["POST retry"]
    end

    subgraph sessionEvidence ["Browser session evidence"]
        needsAction["Needs action count"]
        retryReady["Retry-ready count"]
        lastRetry["Last retry"]
        latestInspect["Latest inspected"]
    end

    subgraph buyerProof ["Buyer proof"]
        visibleRecovery["Recoverable failures"]
        scopedDemo["Demo-scoped evidence"]
        noAdminClaim["No admin claim"]
    end

    buyer -->|"Opens"| root
    root -->|"Shows"| recoveryPanel
    root -->|"Shows"| history
    history -->|"Loads"| detailDrawer
    detailDrawer -->|"Reads"| statusEndpoint
    statusEndpoint -->|"Returns"| needsAction
    statusEndpoint -->|"Returns"| retryReady
    detailDrawer -->|"Enables"| retryButton
    retryButton -->|"Calls"| retryEndpoint
    retryEndpoint -->|"Records"| lastRetry
    detailDrawer -->|"Records"| latestInspect
    recoveryPanel -->|"Summarizes"| needsAction
    recoveryPanel -->|"Summarizes"| retryReady
    recoveryPanel -->|"Summarizes"| lastRetry
    recoveryPanel -->|"Summarizes"| latestInspect
    needsAction -->|"Supports"| visibleRecovery
    retryReady -->|"Supports"| visibleRecovery
    lastRetry -->|"Limits"| scopedDemo
    scopedDemo -->|"Clarifies"| noAdminClaim

    style demoSurface fill:#C2E5FF,stroke:#3DADFF
    style statusApi fill:#CDF4D3,stroke:#66D575
    style sessionEvidence fill:#FFECBD,stroke:#FFC943
    style buyerProof fill:#DCCCFF,stroke:#874FFF
    style noAdminClaim fill:#FFCDC2,stroke:#FF7556
```

### Buyer Integration Deployment Flow

```mermaid
flowchart LR
    subgraph client ["Buyer Surfaces"]
        buyerBrowser["Buyer Browser"]
        powerPlatform["Power Platform"]
        workflowClient["Internal Workflow"]
    end
    subgraph gateway ["Buyer Gateway"]
        buyerGateway["OIDC or S2S Gateway"]
    end
    subgraph service ["Clearfolio Runtime"]
        clearfolioApp["Clearfolio Viewer App"]
    end
    subgraph datastore ["Evidence and Runtime Stores"]
        jobStore["Conversion Job Repository"]
        artifactStore["PDF Artifact Store"]
        localLedgers["Append-only Evidence Ledgers"]
    end
    subgraph external ["Diligence Artifacts"]
        figjamBoard["FigJam Board"]
        qaPack["PR Gate Evidence"]
    end
    subgraph async ["Future Durable Async"]
        durableQueue["Production Queue"]
    end

    buyerBrowser -->|"HTTPS Demo"| buyerGateway
    powerPlatform -->|"Embedded Viewer"| buyerGateway
    workflowClient -->|"S2S Calls"| buyerGateway
    buyerGateway -->|"Signed Tenant Headers"| clearfolioApp
    clearfolioApp -->|"Writes Jobs"| jobStore
    clearfolioApp -->|"Writes Artifacts"| artifactStore
    clearfolioApp -->|"Appends Evidence"| localLedgers
    clearfolioApp -.->|"FigJam: Explains Flow"| figjamBoard
    clearfolioApp -.->|"GitHub: Gate Proof"| qaPack
    clearfolioApp -.->|"Future: Produce Jobs"| durableQueue
    durableQueue -.->|"Future: Consume Jobs"| clearfolioApp
```

### Durable Job Repository Target Architecture

```mermaid
flowchart LR
    subgraph client ["Buyer Surfaces"]
        buyerBrowser["Buyer Browser"]
        powerPlatform["Power Platform"]
    end
    subgraph gateway ["Buyer Gateway"]
        buyerGateway["OIDC or S2S Gateway"]
    end
    subgraph service ["Clearfolio Services"]
        clearfolioApi["Clearfolio Viewer API"]
        conversionWorker["Conversion Worker"]
    end
    subgraph datastore ["Durable Stores"]
        jobDb["Conversion Job DB"]
        objectStore["Artifact Object Store"]
        auditDb["Lifecycle and Audit Events"]
        metricsStore["KPI Projection Store"]
    end
    subgraph async ["Durable Queue"]
        jobQueue["Conversion Job Queue"]
    end
    subgraph external ["Diligence Evidence"]
        figjamBoard["FigJam Board"]
        prEvidence["PR Gate Evidence"]
    end

    buyerBrowser -->|"HTTPS Demo"| buyerGateway
    powerPlatform -->|"Embedded Flow"| buyerGateway
    buyerGateway -->|"Signed Claims"| clearfolioApi
    clearfolioApi -->|"Writes Jobs"| jobDb
    clearfolioApi -->|"Reads Artifacts"| objectStore
    clearfolioApi -->|"Writes Audit"| auditDb
    clearfolioApi -->|"Reads KPIs"| metricsStore
    clearfolioApi -.->|"Produces Jobs"| jobQueue
    jobQueue -.->|"Consumes Jobs"| conversionWorker
    conversionWorker -->|"Persists Transitions"| jobDb
    conversionWorker -->|"Writes Artifacts"| objectStore
    conversionWorker -->|"Writes Events"| auditDb
    clearfolioApi -.->|"FigJam: Explains Target"| figjamBoard
    clearfolioApi -.->|"GitHub: Gate Proof"| prEvidence
```

### Conversion State Store Implementation Flow

```mermaid
flowchart LR
    subgraph api ["API and Worker Entrypoints"]
        submit["submit upload"]
        retry["operator retry"]
        worker["conversion worker"]
    end
    subgraph readBoundary ["Read and Dedupe Boundary"]
        repository["ConversionJobRepository"]
        dedupe["findOrStoreByContentHash"]
    end
    subgraph transitionBoundary ["Lifecycle Transition Boundary"]
        stateStore["ConversionJobStateStore"]
        adapter["RepositoryBacked Adapter"]
        inMemory["InMemory Repository"]
    end
    subgraph jobState ["Job State"]
        submitted["SUBMITTED"]
        processing["PROCESSING"]
        succeeded["SUCCEEDED"]
        failed["FAILED dead-lettered"]
    end
    subgraph buyerProof ["Buyer Evidence"]
        tests["Targeted Tests"]
        gates["Maven Gates"]
        plan["Durable SQL Plan"]
    end

    submit -->|"Stores or reuses"| dedupe
    dedupe -->|"Reads jobs"| repository
    retry -->|"Accepts retry"| stateStore
    worker -->|"Claims job"| stateStore
    stateStore -->|"Delegates fallback"| adapter
    stateStore -->|"Implemented by"| inMemory
    stateStore -->|"Claims"| processing
    stateStore -->|"Schedules retry"| submitted
    stateStore -->|"Marks success"| succeeded
    stateStore -->|"Dead letters"| failed
    processing -.->|"Artifact path"| succeeded
    processing -.->|"Retry exhausted"| failed
    stateStore -->|"Verified by"| tests
    tests -->|"Feeds"| gates
    plan -->|"Next step"| stateStore
```

### Conversion Lifecycle Event Trail Flow

```mermaid
flowchart LR
    subgraph entry ["Current Runtime Entry Points"]
        submit["Upload submit"]
        dedupe["Duplicate upload"]
        worker["Conversion worker"]
        operator["Operator retry"]
    end
    subgraph state ["State Store Boundary"]
        repository["InMemoryConversionJobRepository"]
        stateStore["ConversionJobStateStore"]
    end
    subgraph events ["Process-local Lifecycle Events"]
        submitted["conversion.job.submitted"]
        dedupeHit["conversion.job.dedupe_hit"]
        started["conversion.processing.started"]
        retryScheduled["conversion.retry.scheduled"]
        succeeded["conversion.job.succeeded"]
        failed["conversion.job.failed"]
        retryAccepted["conversion.retry.accepted"]
    end
    subgraph privacy ["Event Redaction Boundary"]
        allowed["job id, tenant, status, attempt, retryAt"]
        omitted["no filename, hash, artifact path, token, raw converter error"]
    end
    subgraph buyerProof ["Buyer Diligence Proof"]
        tests["Repository event-order tests"]
        durablePlan["SQL event table plan"]
        projection["Future KPI projection"]
    end

    submit -->|"findOrStore"| repository
    dedupe -->|"reuse canonical job"| repository
    worker -->|"claim, retry, success, failure"| stateStore
    operator -->|"retry accepted"| stateStore
    stateStore --> repository
    repository --> submitted
    repository --> dedupeHit
    repository --> started
    repository --> retryScheduled
    repository --> succeeded
    repository --> failed
    repository --> retryAccepted
    submitted --> allowed
    started --> allowed
    failed --> omitted
    retryAccepted --> omitted
    allowed --> tests
    omitted --> tests
    tests --> durablePlan
    durablePlan --> projection

    style entry fill:#C2E5FF,stroke:#3DADFF
    style state fill:#CDF4D3,stroke:#66D575
    style events fill:#FFECBD,stroke:#FFC943
    style privacy fill:#DCCCFF,stroke:#874FFF
    style omitted fill:#FFCDC2,stroke:#FF7556
    style buyerProof fill:#D9D9D9,stroke:#B3B3B3
```

### Threat Boundaries and Data Handling

```mermaid
flowchart LR
    browser(["Buyer or operator browser"])
    apiClient(["API client"])

    subgraph edgeLayer ["Untrusted request boundary"]
        upload["POST /api/v1/convert/jobs"]
        status["Status, retry, viewer, analytics APIs"]
        viewerHtml["GET /viewer/{docId}"]
        artifactRead["GET /artifacts/{docId}.pdf"]
    end

    subgraph appLayer ["Clearfolio WebFlux app"]
        validate["Validate extension, size, override headers"]
        jobService["Create job, SHA-256 hash, dedupe"]
        worker["Bounded worker, retry, dead-letter"]
        kpi["Runtime KPI snapshot"]
        headers["Viewer CSP and no-store headers"]
    end

    subgraph memoryLayer ["Process-lifetime in-memory stores"]
        jobRepo[("Conversion job metadata")]
        artifactStore[("Converted PDF bytes")]
    end

    subgraph futureControls ["Production controls still required"]
        auth["Auth, RBAC, tenant scope"]
        signedLinks["Signed artifact links and expiry"]
        durableStore["Encrypted durable store and retention"]
        converterSandbox["Converter sandbox, AV, timeout"]
    end

    browser -->|"Uploads file"| upload
    apiClient -->|"Calls API"| upload
    apiClient -->|"Polls or retries"| status
    browser -->|"Opens viewer"| viewerHtml
    browser -->|"Fetches PDF range"| artifactRead

    upload -->|"Multipart bytes"| validate
    validate -->|"Accepted metadata"| jobService
    jobService -->|"Stores job"| jobRepo
    jobService -->|"Enqueues job"| worker
    worker -->|"Reads job"| jobRepo
    worker -->|"Writes PDF"| artifactStore
    status -->|"Reads lifecycle"| jobRepo
    kpi -->|"Aggregates counters"| jobRepo
    viewerHtml -->|"Applies headers"| headers
    artifactRead -->|"Requires SUCCEEDED"| jobRepo
    artifactRead -->|"Returns no-store PDF"| artifactStore

    jobRepo -.->|"Needs tenant ACL"| auth
    artifactStore -.->|"Needs signed access"| signedLinks
    artifactStore -.->|"Needs retention"| durableStore
    worker -.->|"Needs real converter isolation"| converterSandbox

    style edgeLayer fill:#FFF4CE,stroke:#D29922
    style appLayer fill:#DFF6DD,stroke:#2DA44E
    style memoryLayer fill:#DDF4FF,stroke:#0969DA
    style futureControls fill:#FFEBE9,stroke:#CF222E
```

### License Diligence Closure Flow

```mermaid
flowchart LR
    sbom["CycloneDX SBOM"]
    metadata["License metadata complete"]
    flagged{"Flagged components?"}
    review["Engineering review"]
    legal{"Legal decision"}
    approve["Approve route"]
    attribution["Third-party attribution"]
    replace["Replace dependency"]
    remove["Remove dependency"]
    gate["CI allowlist gate"]
    buyer["Buyer data-room package"]

    sbom -->|"Shows 61 components"| metadata
    metadata -->|"0 unknown"| flagged
    flagged -->|"0 flagged"| review
    review -->|"Classifies risk"| legal
    legal -->|"Allowed"| approve
    legal -->|"Not allowed"| replace
    legal -->|"Not needed"| remove
    approve -->|"Renders notices"| attribution
    attribution -->|"Locks package"| gate
    replace -->|"Rerun SBOM"| sbom
    remove -->|"Rerun SBOM"| sbom
    gate -->|"Prevents drift"| buyer

    style metadata fill:#CDF4D3,stroke:#66D575
    style flagged fill:#FFECBD,stroke:#FFC943
    style legal fill:#FFECBD,stroke:#FFC943
    style replace fill:#FFCDC2,stroke:#FF7556
    style remove fill:#FFCDC2,stroke:#FF7556
    style attribution fill:#C2E5FF,stroke:#3DADFF
    style gate fill:#C2E5FF,stroke:#3DADFF
    style buyer fill:#DCCCFF,stroke:#874FFF
```

### Auth Tenant Boundary Flow

```mermaid
flowchart LR
    caller["Browser or workflow"]
    token["OIDC or S2S token"]
    validate{"Token valid?"}
    principal["Request principal"]
    permission{"Permission allowed?"}
    tenant{"Tenant matches?"}
    route["Viewer API route"]
    audit["Audit event"]
    deny["Stable denial"]
    artifact["Signed artifact link"]
    kpi["Tenant KPI view"]

    caller -->|"Sends bearer"| token
    token -->|"Verify issuer"| validate
    validate -->|"No"| deny
    validate -->|"Yes"| principal
    principal -->|"Checks scope"| permission
    permission -->|"No"| deny
    permission -->|"Yes"| tenant
    tenant -->|"Wrong tenant"| deny
    tenant -->|"Same tenant"| route
    route -->|"Creates"| artifact
    route -->|"Filters"| kpi
    route -->|"Records"| audit
    deny -->|"Records"| audit

    style validate fill:#FFECBD,stroke:#FFC943
    style permission fill:#FFECBD,stroke:#FFC943
    style tenant fill:#FFECBD,stroke:#FFC943
    style deny fill:#FFCDC2,stroke:#FF7556
    style route fill:#C2E5FF,stroke:#3DADFF
    style artifact fill:#CDF4D3,stroke:#66D575
    style kpi fill:#DCCCFF,stroke:#874FFF
    style audit fill:#D9D9D9,stroke:#B3B3B3
```

### Gateway Signed Tenant Claims Flow

```mermaid
flowchart LR
    client["Browser or workflow client"]
    gateway["Trusted gateway or host platform"]
    claims["Tenant claim set"]
    signed["Signed X-Clearfolio headers"]
    service["Clearfolio Viewer API"]
    auth["TenantAccessService"]
    tenant["Tenant-scoped job, KPI, viewer, artifact link APIs"]
    reject["401 rejected auth claims"]
    artifact["Signed artifact token"]
    preview["Viewer preview"]
    hidden["404 hidden resource"]
    demo["Unsigned buyer-demo mode only"]

    client -->|"OIDC or platform auth"| gateway
    gateway -->|"Maps identity to tenant id, subject id, permissions"| claims
    claims -->|"HMAC signs tenant id, subject id, permissions, issued-at"| signed
    signed -->|"POST or GET JSON API"| service
    service -->|"Verify signature and skew when configured"| auth
    auth -->|"Valid signature and permission"| tenant
    auth -->|"Missing, stale, future, or invalid signature"| reject
    tenant -->|"Artifact link create"| artifact
    artifact -->|"Tenant and checksum-bound PDF read"| preview
    tenant -->|"Cross-tenant lookup"| hidden
    service -->|"No hmac secret in local demo"| demo

    style gateway fill:#C2E5FF,stroke:#3DADFF
    style service fill:#C2E5FF,stroke:#3DADFF
    style tenant fill:#C2E5FF,stroke:#3DADFF
    style claims fill:#CDF4D3,stroke:#66D575
    style signed fill:#CDF4D3,stroke:#66D575
    style auth fill:#CDF4D3,stroke:#66D575
    style reject fill:#FFCDC2,stroke:#FF7556
    style hidden fill:#FFCDC2,stroke:#FF7556
    style demo fill:#FFCDC2,stroke:#FF7556
```

### Operator Job Detail Flow

```mermaid
flowchart LR
    history["Session history row"]
    details["Details button"]
    statusApi["Status API"]
    drawer["Job detail drawer"]
    deadLetter{"Dead-lettered?"}
    retry["Retry button"]
    retryApi["Retry API"]
    poll["Status polling"]
    viewer["Open viewer"]
    json["Status JSON"]

    history -->|"Selects"| details
    history -->|"Opens"| json
    details -->|"Fetches"| statusApi
    statusApi -->|"Returns evidence"| drawer
    drawer -->|"Shows attempts"| deadLetter
    deadLetter -->|"Yes"| retry
    deadLetter -->|"No"| viewer
    retry -->|"Operator header"| retryApi
    retryApi -->|"Accepted"| poll
    poll -->|"Succeeded"| viewer

    style drawer fill:#C2E5FF,stroke:#3DADFF
    style deadLetter fill:#FFECBD,stroke:#FFC943
    style retry fill:#DCCCFF,stroke:#874FFF
    style retryApi fill:#CDF4D3,stroke:#66D575
    style viewer fill:#CDF4D3,stroke:#66D575
    style json fill:#D9D9D9,stroke:#B3B3B3
```

### Runtime Tenant Enforcement Flow

```mermaid
flowchart LR
    browser["Buyer demo browser"]
    headers["Tenant headers"]
    api["Protected JSON API"]
    auth["TenantAccessService"]
    permission{"Permission present?"}
    repository["Tenant-aware job repository"]
    owner{"Same tenant?"}
    response["Status, viewer, KPI response"]
    denied["401 or 403"]
    hidden["404 hidden resource"]
    artifact["Signed artifact URL"]
    signed["Artifact token verification"]

    browser -->|"Sends"| headers
    headers -->|"Parsed by"| auth
    browser -->|"Calls"| api
    api -->|"Requires"| auth
    auth --> permission
    permission -->|"No"| denied
    permission -->|"Yes"| repository
    repository --> owner
    owner -->|"No"| hidden
    owner -->|"Yes"| response
    response -->|"Returns"| artifact
    artifact -->|"Read guarded by"| signed

    style auth fill:#C2E5FF,stroke:#3DADFF
    style permission fill:#FFECBD,stroke:#FFC943
    style repository fill:#DCCCFF,stroke:#874FFF
    style response fill:#CDF4D3,stroke:#66D575
    style artifact fill:#DFF7E8,stroke:#1B7F3A
    style signed fill:#FFECBD,stroke:#FFC943
```

### Runtime Signed Artifact Link Flow

```mermaid
flowchart LR
    viewer["Viewer JSON API"]
    job{"Job succeeded and same tenant?"}
    hidden["404 hidden resource"]
    link["ArtifactLinkService creates HMAC token"]
    claims["Claims: jti, tenantId, subjectId, docId, scope, exp, checksum"]
    bootstrap["Bootstrap returns signed previewResourcePath"]
    pdfjs["PDF.js requests /artifacts/{docId}.pdf?artifactToken=..."]
    verify{"Token valid, unexpired, same doc, tenant, checksum?"}
    deny["401 or 403 no-store"]
    range["Serve PDF bytes with Range and no-store headers"]
    api["POST /api/v1/viewer/{docId}/artifact-links"]

    viewer -->|"viewer:read tenant headers"| job
    api -->|"artifact-link:create tenant headers"| job
    job -->|"No"| hidden
    job -->|"Yes"| link
    link --> claims
    claims --> bootstrap
    bootstrap --> pdfjs
    pdfjs --> verify
    verify -->|"No"| deny
    verify -->|"Yes"| range

    style hidden fill:#FFE2E2,stroke:#D92D20
    style deny fill:#FFE2E2,stroke:#D92D20
    style link fill:#E8F3FF,stroke:#2374AB
    style verify fill:#FFECBD,stroke:#FFC943
    style range fill:#DFF7E8,stroke:#1B7F3A
```

### Artifact Revocation And Read Audit Flow

```mermaid
flowchart LR
    viewer["Viewer or operator"]
    linkApi["POST artifact-links"]
    ledger["Runtime artifact link ledger"]
    artifactApi["GET artifacts doc.pdf"]
    tokenCheck["Token verification"]
    pdfBytes["PDF byte response"]
    audit["Artifact read audit events"]
    operator["Operator or tenant admin"]
    revokeApi["POST artifact-links token revoke"]
    denied["Artifact read blocked"]
    reviewer["Buyer reviewer"]
    auditApi["GET artifact-read-events"]

    viewer -->|"Create signed link"| linkApi
    linkApi -->|"Record token metadata"| ledger
    viewer -->|"Read PDF with artifactToken"| artifactApi
    artifactApi -->|"Verify signature expiry scope doc tenant checksum"| tokenCheck
    tokenCheck -->|"Check token is known and active"| ledger
    ledger -->|"Active token"| artifactApi
    artifactApi -->|"Serve full range or 416"| pdfBytes
    artifactApi -->|"Record status range trace"| audit
    operator -->|"Revoke token"| revokeApi
    revokeApi -->|"Mark token revoked"| ledger
    ledger -->|"Revoked token"| tokenCheck
    tokenCheck -->|"Return 403"| denied
    reviewer -->|"Read audit evidence"| auditApi
    auditApi -->|"Tenant filtered events"| audit

    style linkApi fill:#E8F3FF,stroke:#2F6BFF
    style artifactApi fill:#E8F3FF,stroke:#2F6BFF
    style revokeApi fill:#E8F3FF,stroke:#2F6BFF
    style auditApi fill:#E8F3FF,stroke:#2F6BFF
    style ledger fill:#FFF3E3,stroke:#B95D00
    style audit fill:#FFF3E3,stroke:#B95D00
    style denied fill:#FFE2E2,stroke:#D92D20
```

### File Backed Artifact Ledger Flow

```mermaid
flowchart LR
    service["ArtifactLinkLedger bean"]
    config{"Path configured?"}
    memory["Process memory"]
    issued["ISSUED metadata"]
    revoked["REVOKED metadata"]
    read["READ event"]
    file[("Append-only ledger file")]
    restart["Service restart"]
    replay["Replay ledger"]
    valid{"Valid order?"}
    recovered["Recovered ledger state"]
    fail["Fail startup with invalid ledger"]
    production["Future durable store"]

    service --> config
    config -->|"No"| memory
    config -->|"Yes"| file
    service -->|"Records"| issued
    service -->|"Revokes"| revoked
    service -->|"Audits"| read
    issued -->|"Append"| file
    revoked -->|"Append first revoke"| file
    read -->|"Append"| file
    file -->|"Boot"| restart
    restart --> replay
    replay --> valid
    valid -->|"Yes"| recovered
    valid -->|"No"| fail
    recovered -.->|"Not centralized"| production

    style config fill:#FFECBD,stroke:#FFC943
    style memory fill:#DDF4FF,stroke:#0969DA
    style file fill:#FFF3E3,stroke:#B95D00
    style recovered fill:#CDF4D3,stroke:#66D575
    style fail fill:#FFE2E2,stroke:#D92D20
    style production fill:#DCCCFF,stroke:#874FFF
```

### KPI Snapshot Evidence Ledger Flow

```mermaid
flowchart LR
    buyer["Buyer or operator"]
    api["GET /api/v1/analytics/kpi-snapshot"]
    auth["TenantAccessService"]
    repo[("Tenant jobs")]
    snapshot["KPI snapshot response"]
    config{"Path configured?"}
    memory["Runtime ledger"]
    file[("Append-only snapshot file")]
    evidence["GET /api/v1/analytics/kpi-snapshot-exports"]
    replay["Replay on startup"]
    future["Future durable event stream"]

    buyer -->|"Signed tenant headers"| api
    api -->|"Requires analytics:read"| auth
    auth -->|"Tenant filter"| repo
    repo -->|"Counts jobs"| snapshot
    api -->|"Records export"| config
    config -->|"No"| memory
    config -->|"Yes"| file
    buyer -->|"Inspect exports"| evidence
    evidence -->|"Tenant-scoped read"| memory
    evidence -->|"Tenant-scoped read"| file
    file -->|"Boot"| replay
    replay -.->|"Partial evidence"| future
    snapshot -->|"Returned to buyer"| buyer

    style auth fill:#C2E5FF,stroke:#3DADFF
    style config fill:#FFECBD,stroke:#FFC943
    style snapshot fill:#CDF4D3,stroke:#66D575
    style evidence fill:#C6FAF6,stroke:#5AD8CC
    style file fill:#FFF3E3,stroke:#B95D00
    style future fill:#DCCCFF,stroke:#874FFF
```

### KPI Snapshot Export Evidence API Flow

```mermaid
flowchart LR
    reviewer["Buyer reviewer"]
    exports["GET /api/v1/analytics/kpi-snapshot-exports"]
    auth["TenantAccessService"]
    ledger["KpiSnapshotLedger"]
    filter{"Same tenant?"}
    response["KpiSnapshotExportResponse"]
    hidden["Excluded export"]
    durable["Future analytics events"]

    reviewer -->|"Signed analytics:read"| exports
    exports -->|"Requires permission"| auth
    auth -->|"Tenant id"| ledger
    ledger -->|"Snapshot records"| filter
    filter -->|"Yes"| response
    filter -->|"No"| hidden
    response -.->|"Temporary read model"| durable

    style auth fill:#C2E5FF,stroke:#3DADFF
    style ledger fill:#FFF3E3,stroke:#B95D00
    style filter fill:#FFECBD,stroke:#FFC943
    style response fill:#CDF4D3,stroke:#66D575
    style hidden fill:#FFCDC2,stroke:#FF7556
    style durable fill:#DCCCFF,stroke:#874FFF
```
