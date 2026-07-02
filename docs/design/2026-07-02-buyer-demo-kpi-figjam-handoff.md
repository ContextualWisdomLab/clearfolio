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
