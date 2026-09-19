# Clearfolio Document Fidelity Acceptance

Status: Canonical product-quality gate
Baseline: protected `main` at `f3cc09a9838f0f88c81a2ceae22138fab80a2edb`

This document defines what Clearfolio may call a supported document conversion. It is deliberately stricter than “the endpoint returned a PDF.” Conversion support is a buyer-facing claim and therefore requires deterministic, reproducible evidence.

## Maturity vocabulary

- `IMPLEMENTED_ON_MAIN`: protected-main behavior exists and may be described as current, subject to the evidence limits below.
- `ACTIVE_PR`: implementation exists only on an open pull request and is not shipped behavior.
- `PARTIAL`: a usable boundary exists but does not yet satisfy the full production-fidelity contract.
- `ACCEPTED_ARCHITECTURE`: the design is accepted but implementation/evidence is incomplete.
- `PLANNED`: backlog only.

## Current support truth

| Source class | Current maturity | Allowed product claim |
| --- | --- | --- |
| PDF passthrough with `%PDF-` validation | `IMPLEMENTED_ON_MAIN` | Native PDF bytes can be stored and served through the artifact path subject to validation/security gates. |
| Non-PDF placeholder generation | `IMPLEMENTED_ON_MAIN` as development/demo behavior | A one-page development/demo PDF may be generated. This is **not** Office rendering or fidelity evidence. |
| DOCX/XLSX/PPTX production conversion | `PLANNED` / issue #5 | No production support claim until a sandboxed adapter and realistic fixture evidence pass this contract. |
| HWP/HWPX | `PLANNED` / currently blocked by policy | Unsupported unless a separately reviewed format/security/fidelity decision changes the policy. |

The existence of a file extension parser, MIME acceptance path, placeholder PDF, or successful HTTP response is never sufficient evidence for a production-format support claim.

## Production conversion acceptance

A format may move to production-supported status only when one exact integrated protected head proves all applicable gates.

### Deterministic rendering

- identical source bytes, converter version/configuration, fonts, locale, and declared platform profile produce semantically equivalent output;
- network access is not required by default and is denied at the converter boundary; conversion must not dereference external hyperlinks or fetch remote resources;
- network isolation does not by itself authorize stripping inert URI/internal hyperlinks from output; fidelity fixtures must prove legitimate hyperlinks remain usable unless a versioned product policy explicitly disallows them;
- converter/version/configuration and source/output digests are recorded in provenance evidence;
- nondeterministic metadata is normalized or explicitly excluded from the equality model without hiding semantic differences.

### Realistic fixtures and rights

The benchmark corpus must contain authorized or redistributable fixtures representative of actual buyer documents. Synthetic-only examples may supplement but not replace realistic fixtures. Fixture provenance and redistribution rights must be documented.

At minimum, fixtures must exercise where the format supports them:

- text, fonts, Unicode and Korean text;
- tables, pagination and page breaks;
- images and transparency;
- headers/footers, sections and orientation;
- hyperlinks and annotations;
- charts/drawings or other embedded visual objects;
- formulas, merged cells and print areas for spreadsheets;
- slide/page geometry for presentations;
- malformed and adversarial inputs.

### Fidelity assertions

Evidence must be machine-comparable where practical and manually inspectable where semantics cannot be reduced safely to one metric. The suite should include:

- page count and page geometry;
- normalized extracted text and ordering;
- rendered-page image comparison with documented tolerances;
- object/table/cell/slide assertions relevant to the source format;
- no silent page/object loss;
- controlled degraded-mode classification when exact fidelity is impossible.

One global similarity score is not sufficient to authorize a format claim when a critical semantic object can disappear while the score remains high.

### Security and resource bounds

- conversion runs in a bounded sandbox/process boundary appropriate to the selected adapter;
- active content/macros/embedded executables are rejected, neutralized, or handled by an explicit reviewed policy;
- external-link dereferencing and remote-resource loading are disabled by default; inert output URI/internal-link actions may be preserved for fidelity, but executable action classes such as script, launch, submit/import, or remote-execution behavior require explicit fail-closed policy and regression evidence;
- CPU, memory, file size, page/object count, recursion/archive expansion and wall-clock limits are finite;
- converter failures produce controlled error states and never substitute placeholder success;
- temporary files are app-owned, bounded and cleaned after success/failure/cancellation;
- artifacts remain subject to tenant authorization and signed-delivery controls.

### Failure taxonomy

Every conversion attempt is classifiable as one of:

- `native` — no semantic transform, such as accepted PDF passthrough;
- `transformed` — production adapter completed and fidelity/security gates passed;
- `degraded` — a documented non-critical fidelity limitation occurred and the API/UI surfaces it explicitly;
- `unsupported` — no approved adapter/format policy exists;
- `failed` — approved processing started but could not produce an accepted artifact;
- `development_placeholder` — non-production/demo output only.

A `development_placeholder` result must never be emitted as `transformed` or used in production-fidelity KPI evidence.

## Release evidence

For every supported transformed format, a release candidate must preserve:

- fixture manifest with source licenses/rights metadata and integrity digests;
- converter binary/package/version/configuration provenance;
- deterministic focused and full-suite results from the exact release head;
- security/resource-bound regressions;
- representative rendered outputs or privacy-safe derived evidence;
- rollback criteria when the converter/runtime version changes.

Historical evidence from another converter version, head SHA, platform profile, or fixture corpus does not transfer automatically.

## Adapter qualification workflow

Issue #5 remains the product gap for the first sandboxed Office adapter. Qualification order is:

1. establish a legal/redistributable fixture corpus and expected semantics;
2. pin and sandbox the converter/runtime;
3. add RED tests that currently demonstrate unsupported/placeholder behavior;
4. implement the smallest adapter boundary;
5. prove deterministic rendering, security limits, failure taxonomy and provenance;
6. run exact-head product/security/package evidence;
7. update PRD/TRD/API/support matrix only after the implementation is integrated.

The selected adapter must remain replaceable behind a narrow Clearfolio-owned contract so standalone operation and MSA composition do not depend on one vendor-specific storage or orchestration design.

## Traceability

- Product support claims: `docs/PRD.md`
- Technical conversion boundary: `docs/TRD.md`
- Architecture and component ownership: `ARCHITECTURE.md`
- Decision record: `docs/adr/0005-deterministic-conversion-fidelity.md`
- Test strategy: `docs/TEST_STRATEGY.md`
- Standards/research index: `docs/RESEARCH_TRACEABILITY.md`
- Requirement/evidence mapping: `docs/TRACEABILITY.md`
