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
- Figma Code Connect: not used.

## Product Design Acceptance

- The first viewport must show the product name, upload action, and live KPI
  strip without requiring navigation.
- KPI labels must map to buyer-readable outcomes: runtime jobs, ready previews,
  conversion success rate, and p95 preview latency.
- Upload, status tracking, preview handoff, and evidence flow must remain visible
  as one buyer-demo journey rather than separate marketing pages.
- The UI must retain keyboard-accessible controls, visible focus, and live status
  announcements for upload and conversion state changes.
- KPI fallback behavior must not create contradictory buyer evidence: backend
  runtime metrics are primary, browser-session history is fallback only.

## Data Analytics Mapping

| UI KPI | API field | Buyer proof |
| --- | --- | --- |
| Runtime jobs | `totalJobs` | Shows observable conversion workload in the current runtime. |
| Ready | `succeededJobs` | Shows previewable documents available for buyer inspection. |
| Success rate | `conversionSuccessRate` | Shows conversion reliability as an acquisition diligence metric. |
| P95 preview | `p95TimeToPreviewMs` | Shows latency evidence for the demo path. |

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
    replace["Replace dependency"]
    remove["Remove dependency"]
    gate["CI allowlist gate"]
    buyer["Buyer data-room package"]

    sbom -->|"Shows 142 components"| metadata
    metadata -->|"0 unknown"| flagged
    flagged -->|"6 flagged"| review
    review -->|"Classifies risk"| legal
    legal -->|"Allowed"| approve
    legal -->|"Not allowed"| replace
    legal -->|"Not needed"| remove
    approve -->|"Locks policy"| gate
    replace -->|"Rerun SBOM"| sbom
    remove -->|"Rerun SBOM"| sbom
    gate -->|"Prevents drift"| buyer

    style metadata fill:#CDF4D3,stroke:#66D575
    style flagged fill:#FFECBD,stroke:#FFC943
    style legal fill:#FFECBD,stroke:#FFC943
    style replace fill:#FFCDC2,stroke:#FF7556
    style remove fill:#FFCDC2,stroke:#FF7556
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
