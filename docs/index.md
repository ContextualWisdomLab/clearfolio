# Clearfolio

Clearfolio is ContextualWisdomLab's secure document conversion and viewing service. It accepts bounded document jobs, exposes tenant-scoped conversion state, and delivers verified PDF artifacts through a browser viewer and controlled artifact-link flows.

> This landing reflects protected `main` product truth. Active pull requests, planned Office-format fidelity, production identity integrations, and unpublished deployment/release evidence are not presented as shipped capabilities.

## Start here

- [Repository overview and local run guide](https://github.com/ContextualWisdomLab/clearfolio#readme)
- [Architecture](architecture.md)
- [Product requirements](prd-integrated-document-viewer-platform.md)
- [Technical requirements](trd-integrated-document-viewer-platform.md)
- [Buyer deployment integration](deployment/2026-07-02-buyer-deployment-integration-playbook.md)
- [Threat model and data handling](security/2026-07-02-threat-model-data-handling.md)
- [Signed artifact-link design](security/2026-07-02-signed-artifact-link-design.md)
- [Authentication and tenant model](security/2026-07-02-auth-tenant-model.md)
- [Durable job repository plan](persistence/2026-07-02-durable-conversion-job-repository-plan.md)
- [Durable metrics event model](analytics/2026-07-02-durable-metrics-event-model.md)
- [Buyer diligence index](diligence/2026-07-02-buyer-diligence-index.md)
- [Repository releases](https://github.com/ContextualWisdomLab/clearfolio/releases)
- [Ask DeepWiki](https://deepwiki.com/ContextualWisdomLab/clearfolio)

## Product responsibility

Clearfolio owns the document-conversion/viewing boundary, tenant-scoped job lifecycle, artifact-delivery controls, viewer bootstrap, retry/recovery evidence available in the current runtime, and the public API contracts that expose those capabilities. Higher-level hosts may compose Clearfolio through explicit contracts, but they do not acquire direct ownership of Clearfolio application state.

The protected default branch currently proves validated PDF passthrough and viewer behavior plus bounded asynchronous conversion/job primitives. Development placeholders and planned provider adapters are not treated as faithful production conversion for unsupported formats. Header-based buyer-demo identity scaffolding is likewise not a claim of complete production OIDC/JWT federation.

## Security and evidence

Uploaded documents and document-derived values are untrusted input. Cross-tenant access, artifact tokens, permission checks, expiry, revocation, integrity, range delivery, and audit evidence are separate controls. Source and tests are evidence of implemented contracts; deployment, certification, durability, and fidelity claims require their own runtime or release proof.

## Publication boundary

This file is only a GitHub Pages source candidate. Pages is live only after protected integration, repository Pages configuration/deployment through the organization-owned control path, and verification of the resulting HTTPS content.
