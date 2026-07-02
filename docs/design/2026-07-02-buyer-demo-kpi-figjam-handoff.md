# Buyer Demo KPI FigJam Handoff

Date: 2026-07-02

## Figma Artifact

- FigJam: [Clearfolio Buyer Demo Evidence Flow](https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM?utm_source=codex&utm_content=edit_in_figjam&oai_id=&request_id=41b7cd77-c07e-475e-bd77-460b5911666c)
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
