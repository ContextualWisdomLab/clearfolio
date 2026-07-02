# KRW 2B Valuation and KPI Model

Date: 2026-07-02

This document converts the sale-readiness goal into an auditable operating
model. It is not a valuation opinion, investment recommendation, or fairness
opinion. It is a buyer-readiness model for deciding what evidence Clearfolio
Viewer must produce before a KRW 2B acquisition or licensing conversation is
credible.

## Current Market Anchors

| Anchor | Public source | 2026 planning use |
| --- | --- | --- |
| USD/KRW | Trading Economics reported USD/KRW around 1,555 on 2026-07-02. | KRW 2B is treated as approximately USD 1.29M for this model. |
| Enterprise content management | MarketsandMarkets estimates ECM at USD 59.53B in 2026 and USD 95.76B in 2031. | Clearfolio sits in a large enterprise content workflow budget, not a small utility-only category. |
| Document management systems | Grand View Research estimates DMS at USD 7.68B in 2024, USD 8.70B in 2025, and USD 18.17B by 2030. | A focused secure preview wedge can be positioned inside document management and workflow modernization spend. |
| Public SaaS revenue multiples | Aventis Advisors reports a median EV/revenue multiple of 3.4x as of March 2026. | Conservative ARR hurdle for KRW 2B should not assume premium multiples. |
| Cloud software multiples | Clouded Judgement reported a 4.1x median NTM revenue multiple on 2026-01-30. | Base case can use a mid-single-digit public SaaS reference only with credible product and KPI evidence. |
| B2B SaaS multiples | Finerva reports B2B SaaS revenue multiples settled at 5.9x in 2025 in its 2026 report. | Upside case requires proof of retention, growth, gross margin, and integration defensibility. |

Sources:

- Trading Economics USD/KRW quote:
  <https://tradingeconomics.com/south-korea/currency>
- MarketsandMarkets enterprise content management market:
  <https://www.marketsandmarkets.com/Market-Reports/enterprise-content-management-market-226977096.html>
- Grand View Research document management system market:
  <https://www.grandviewresearch.com/industry-analysis/document-management-system-market-report>
- Aventis Advisors SaaS valuation multiples:
  <https://aventis-advisors.com/saas-valuation-multiples/>
- Clouded Judgement 2026-01-30 cloud software multiples:
  <https://cloudedjudgement.substack.com/p/clouded-judgement-13026-software>
- Finerva B2B SaaS 2026 valuation multiples:
  <https://finerva.com/report/b2b-saas-2026-valuation-multiples/>

## Valuation Hurdle

Working FX assumption:

```text
KRW 2,000,000,000 / 1,555 KRW per USD = about USD 1,286,000
```

ARR required at different revenue multiples:

| Case | Revenue multiple | Implied ARR, USD | Implied ARR, KRW | Interpretation |
| --- | ---: | ---: | ---: | --- |
| Conservative | 3.4x | 378K | 588M | Requires real paying demand or strong strategic licensing value. |
| Base | 4.1x | 314K | 488M | Requires credible recurring revenue path and buyer demo evidence. |
| Upside | 5.9x | 218K | 339M | Requires differentiated retention, integration, and margin proof. |

Formula:

```text
required_arr = target_enterprise_value / revenue_multiple
```

The practical sale-readiness bar remains KRW 400M to KRW 650M ARR path because
small private assets are discounted unless a buyer sees clear strategic value,
low integration cost, low support burden, and a credible expansion story.

## Pricing Scenarios

| Scenario | Monthly price per tenant | Tenants for KRW 400M ARR | Tenants for KRW 650M ARR | What must be true |
| --- | ---: | ---: | ---: | --- |
| Internal team | KRW 1.5M | 23 | 37 | Lightweight deployment, low support, self-serve onboarding. |
| Department | KRW 3.0M | 12 | 19 | Admin evidence, auditability, support workflow, reliability. |
| Enterprise | KRW 7.5M | 5 | 8 | Integration playbook, RBAC, signed artifacts, observability. |
| Strategic license | KRW 50M yearly | 8 | 13 | Reusable deployment package and buyer-owned integration path. |

These are planning scenarios. The next evidence requirement is not a larger
feature list; it is proof that one scenario can be demonstrated, priced, and
supported with low buyer risk.

## KPI Decision Tree

Use these metrics to decide whether the product is moving toward the KRW 2B
threshold or just accumulating code.

| KPI | Current repo evidence | Sale-readiness threshold | Decision it supports |
| --- | --- | --- | --- |
| Successful preview rate | `GET /api/v1/analytics/kpi-snapshot` exposes `conversionSuccessRate`. | 99.5 percent for supported formats in a realistic pilot. | Reliability discount or premium. |
| P95 time to preview | KPI endpoint exposes `p95TimeToPreviewMs`. | Less than 10 seconds for small PDF and Office pilot files. | Workflow-speed claim. |
| Runtime volume | KPI endpoint exposes `totalJobs`. | 50K monthly documents after pilot phase. | Usage depth and infrastructure need. |
| Ready previews | KPI endpoint exposes `succeededJobs`. | Increasing share of total jobs with low support tickets. | Buyer demo and adoption quality. |
| Failed and dead-lettered jobs | KPI endpoint exposes `failedJobs` and `deadLetteredJobs`. | Downward trend, with operator recovery evidence. | Operational risk and support burden. |
| Active tenants | Not implemented yet. | 3 design partners, then 10 paid tenants. | Demand proof. |
| Trial to paid conversion | Not implemented yet. | 25 percent or higher for ICP pilots. | Commercial repeatability. |
| Gross margin | Not implemented yet. | More than 75 percent at steady usage. | SaaS quality. |

## Evidence Gaps

1. **Durable metrics:** KPI snapshot is runtime-only; it must move to durable
   event storage before it can prove monthly volume or retention.
2. **Tenant dimension:** Current jobs are not tenant-scoped, so the model cannot
   prove active tenants, tenant expansion, or tenant-specific reliability.
3. **Cost model:** Current conversion artifacts are in memory; gross margin
   proof requires artifact storage, conversion compute, and support-cost inputs.
4. **Security posture:** PR evidence includes Semgrep, Maven gates, threat
   model, data handling map, and generated SBOM evidence, but buyer diligence
   still needs license allowlist review, signed artifacts, auth/RBAC, and
   tenant isolation.
5. **Integration packaging:** A buyer still needs a deployment and integration
   playbook for Power Platform, mobile/tablet, and internal workflow embedding.

## Next Evidence Slices

1. Complete license allowlist review against the generated CycloneDX SBOM.
2. Implement durable metric events after tenant and deployment design are ready.
3. Add tenant-aware KPI fields only when the tenant model exists.
4. Implement signed artifact links after auth and tenant design.
5. Add seeded demo data so local demo, FigJam, and buyer deck tell the same story.
