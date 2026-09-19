# Clearfolio Standards and Research Traceability

Status: Canonical standards/research index
Reviewed: 2026-08-10

This document records the primary standards, official technical documentation, and peer-reviewed research that materially constrain Clearfolio architecture and acceptance. It does not convert a standard into an implementation claim; `docs/TRACEABILITY.md` remains the requirement→source/test/PR evidence map.

## Standards and technical-source mapping

| Area | Primary source | Clearfolio decision / acceptance consequence |
| --- | --- | --- |
| HTTP byte ranges | Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110, STD 97). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110 | Signed artifact delivery implements a deliberately narrower zero-or-one `bytes` Range profile; valid partial responses use correct `Content-Range`/`Accept-Ranges`, and malformed/unsatisfiable/multiple ranges fail under the reviewed endpoint contract instead of silently broadening behavior. |
| Web accessibility | World Wide Web Consortium. (2024, December 12). *Web Content Accessibility Guidelines (WCAG) 2.2* (W3C Recommendation). https://www.w3.org/TR/2024/REC-WCAG22-20241212/ | Repeated viewer controls require distinguishable accessible names; async/busy/focus/keyboard states need explicit tests. Viewer accessibility claims require measured acceptance rather than visual inspection alone. Clearfolio pins the December 2024 Recommendation publication for reproducible implementation evidence rather than a moving latest-version URI. |
| Kubernetes probes | Kubernetes Authors. (2026, April 17). *Liveness, readiness, and startup probes*. Kubernetes Documentation. https://kubernetes.io/docs/concepts/workloads/pods/probes/ | Liveness answers restart eligibility; readiness answers traffic eligibility. Shared/transient dependency failures must not casually turn liveness into a restart cascade. This grounds ADR-0006 and active PR #295. |
| Spring availability model | Broadcom. (2026). *Spring Boot 3.5 reference: Actuator endpoints—Kubernetes probes*. https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes | Spring `ApplicationAvailability` and the dedicated liveness/readiness model support Clearfolio's explicit availability semantics. Application construction should not invent availability state when the required Spring authority is absent. The 3.5 documentation URI is version-pinned; adopting another Spring line requires a fresh compatibility check. |
| Telemetry architecture | OpenTelemetry Authors. (2025). *OpenTelemetry Specification 1.59.0*. https://opentelemetry.io/docs/specs/otel/ | Planned traces/metrics use OTel-compatible concepts while preserving privacy-safe low-cardinality attributes. No SLO is claimed merely because telemetry instrumentation exists. The official page identifies the current specification version; implementation must pin the dependency/specification combination it actually qualifies. |
| CycloneDX SBOM | Ecma International. (2025). *CycloneDX Bill of materials specification* (ECMA-424, 2nd ed.; CycloneDX v1.7). https://ecma-international.org/publications-and-standards/standards/ecma-424/ | The authoritative current ECMA-424 edition defines CycloneDX v1.7. Clearfolio's committed/active supply-chain evidence remains a historical CycloneDX 1.6 baseline until a separately reviewed implementation migration changes it. Documentation of the current standard does not silently upgrade generated SBOM schema versions. |
| SPDX | SPDX Workgroup. (2024). *SPDX Specification 3.0.1*. Linux Foundation. https://spdx.github.io/spdx-spec/v3.0.1/ | SPDX is an accepted interoperable software/supply-chain metadata model where required by release tooling. Adoption of a specific serialization/version is an implementation/release decision, not a generic documentation claim. |
| Software provenance | SLSA Community. (2025). *SLSA specification, version 1.2*. https://slsa.dev/spec/v1.2/ | Release provenance should make source/build/material identity verifiable. Clearfolio uses SLSA concepts as a target quality model; do not claim a SLSA level without conformance evidence. |
| Secure development | Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure Software Development Framework (SSDF) Version 1.1: Recommendations for mitigating the risk of software vulnerabilities* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218 | Root-cause remediation, dependency/supply-chain controls, secure release practices, and documented security gates align with SSDF principles. The repository does not claim NIST certification. |
| Office conversion process boundary | JODConverter. (2025). *Office Managers*; JODConverter. (2025). *Configuration overview*; JODConverter. (2025). *Release 4.4.11*; JODConverter. (2025). *Migration 4.4.11*. | Current JODConverter documentation distinguishes local, externally managed local, and remote office managers and documents task/process lifecycle controls. Clearfolio deliberately rejects starting the office runtime inside the API-container trust boundary: issue #5 requires a sandboxed sidecar/external process or separately operated remote service behind a provider-neutral adapter. The existence of `LocalOfficeManager` support is not a Clearfolio architecture approval. |
| Office runtime licensing | The Document Foundation. (n.d.). *Licenses*. https://www.libreoffice.org/licenses/ | LibreOffice is made available under MPL-2.0 and includes components under other licenses that can vary by distribution/version. Qualification therefore reviews the exact image/runtime/fonts/codecs/dictionaries and generated SBOM/attribution rather than inferring redistribution approval from the Java adapter license. |

## Current Office-adapter source refresh

The product-gap issue originally cited JODConverter 4.4.10 configuration as if that represented the current documented line. Official project documentation now identifies release 4.4.11 and its migration guide, while the current `Office Managers` and `Configuration overview` pages are published under the project's rolling `/latest/` documentation tree. A search of the official published site on 2026-08-09 found a fixed 4.4.10 documentation tree but did **not** establish equivalent fixed `4.4.11/getting-started/office-managers/` or `4.4.11/configuration/` pages. Those two rolling pages are therefore cited with a retrieval date instead of inventing a versioned URL that was not verified.

The version qualification anchor is the explicit 4.4.11 release/migration content, not the word `latest`. Clearfolio must pin the exact reviewed adapter and office-runtime versions in dependency/image configuration, exercise the realistic fidelity/security corpus, and record them in SBOM/provenance evidence. A future documentation refresh should replace a rolling source with a fixed project URL if the project publishes and verifies one.

APA 7 technical-source entries used for the Office-adapter decision:

- JODConverter. (2025). *Office Managers*. Retrieved August 9, 2026, from https://jodconverter.github.io/jodconverter/latest/getting-started/office-managers/
- JODConverter. (2025). *Configuration overview*. Retrieved August 9, 2026, from https://jodconverter.github.io/jodconverter/latest/configuration/
- JODConverter. (2025, August 21). *Release 4.4.11*. https://jodconverter.github.io/jodconverter/latest/release-notes/release-notes-4.4.11/
- JODConverter. (2025). *Migration 4.4.11*. https://jodconverter.github.io/jodconverter/latest/migration-guides/migration-guide-4.4.11/
- JODConverter. (2025, August 21). *Apache License, Version 2.0* [License file, v4.4.11]. GitHub. https://raw.githubusercontent.com/jodconverter/jodconverter/v4.4.11/LICENSE
- The Document Foundation. (n.d.). *Licenses*. https://www.libreoffice.org/licenses/

The fixed JODConverter 4.4.10 documentation root remains useful provenance for the previously reviewed line but is not substituted for 4.4.11 behavior: https://jodconverter.github.io/jodconverter/4.4.10/

## Stable APA 7 reference entries

- Broadcom. (2026). *Spring Boot 3.5 reference: Actuator endpoints*. https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html
- Ecma International. (2025). *CycloneDX Bill of materials specification* (ECMA-424, 2nd ed.; CycloneDX v1.7). https://ecma-international.org/publications-and-standards/standards/ecma-424/
- Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110, STD 97). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110
- Kubernetes Authors. (2026, April 17). *Liveness, readiness, and startup probes*. https://kubernetes.io/docs/concepts/workloads/pods/probes/
- OpenTelemetry Authors. (2025). *OpenTelemetry Specification 1.59.0*. https://opentelemetry.io/docs/specs/otel/
- OWASP Foundation & Ecma International. (2024). *CycloneDX Bill of Materials Standard, version 1.6 (ECMA-424, 1st ed.)* [Historical Clearfolio SBOM baseline]. https://cyclonedx.org/news/cyclonedx-v1.6-now-an-ecma-international-standard/
- SLSA Community. (2025). *SLSA specification, version 1.2*. https://slsa.dev/spec/v1.2/
- Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure Software Development Framework (SSDF) Version 1.1: Recommendations for mitigating the risk of software vulnerabilities* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218
- SPDX Workgroup. (2024). *SPDX Specification 3.0.1*. Linux Foundation. https://spdx.github.io/spdx-spec/v3.0.1/
- World Wide Web Consortium. (2024, December 12). *Web Content Accessibility Guidelines (WCAG) 2.2*. https://www.w3.org/TR/2024/REC-WCAG22-20241212/

## Peer-reviewed testing evidence

### Coverage is a structural gate, not a product oracle

Inozemtseva and Holmes empirically found that coverage is not strongly correlated with test-suite effectiveness once suite size is controlled. Clearfolio therefore retains exact 100% owned production statement/branch coverage as a structural contract **and separately requires realistic security, lifecycle, fidelity, accessibility, concurrency, crash/restart, migration/rollback, and release assertions**.

APA 7:

Inozemtseva, L., & Holmes, R. (2014). Coverage is not strongly correlated with test suite effectiveness. In *Proceedings of the 36th International Conference on Software Engineering* (pp. 435–445). Association for Computing Machinery. https://doi.org/10.1145/2568225.2568271

### Test results require trustworthy oracles/evidence

The software-testing oracle problem explains why “the command exited zero” is not a complete correctness claim. Clearfolio's Maven evidence verifier therefore checks that tests actually executed, were not skipped, and report zero failures/errors, while domain-specific tests assert security/fidelity/recovery outcomes.

APA 7:

Barr, E. T., Harman, M., McMinn, P., Shahbaz, M., & Yoo, S. (2015). The oracle problem in software testing: A survey. *IEEE Transactions on Software Engineering, 41*(5), 507–525. https://doi.org/10.1109/TSE.2014.2372785

## Decision notes

### RFC 9110 does not require Clearfolio to implement every multi-range feature

The standard permits one or more ranges; Clearfolio intentionally supports a smaller single-range product contract. That restriction must be explicit and consistently rejected/tested, not accidentally implemented differently across two byte-delivery endpoints.

### Accessibility conformance is more than ARIA strings

Contextual accessible names in active PR #264 are necessary for repeated actions, but full viewer accessibility also depends on keyboard/focus/state/error/print behavior and must remain `PARTIAL` until those paths have acceptance evidence.

### Liveness is intentionally narrower than readiness

Kubernetes documentation warns that poorly designed liveness probes can amplify failures. Clearfolio therefore treats restart-worthiness as a process property while routing readiness may include temporary instance conditions. External shared-service health is not automatically a reason to restart the process.

### SBOM and provenance formats are versioned products

The current standard line is CycloneDX v1.7 / ECMA-424 2nd edition, while Clearfolio's repository evidence is still a CycloneDX 1.6 historical baseline. The existence of either a CycloneDX/SPDX document is not supply-chain assurance by itself. Release acceptance binds generated metadata to exact source/dependency/build identity and verifies deterministic regeneration/provenance as applicable; a schema upgrade requires its own implementation and compatibility evidence.

### Office conversion support requires two independent evidence classes

JODConverter's API/process-management features can support a conversion adapter, but they do not prove document fidelity or sandbox safety. Clearfolio therefore requires both (1) a provider-neutral isolated execution boundary with bounded resources/network/secrets and (2) realistic authorized fixture evidence under `docs/FIDELITY_ACCEPTANCE.md`. A successful LibreOffice/JODConverter process exit is not a product-support oracle.

## Source lifecycle rule

For stable standards, cite the normative/stable publication. For fast-moving project specifications such as OpenTelemetry, CycloneDX, SLSA, Spring Boot, Kubernetes, JODConverter, and LibreOffice packaging, re-check the official current source before making a new version-specific claim. Do not encode a transient “latest version” statement in timeless architecture unless the project deliberately pins and tests that version.

## PDF / copyright policy

A paper PDF is committed only when redistribution is explicitly permitted. Otherwise retain the full citation, DOI/stable source link, and a concise relevance summary. Documentation completeness does not justify copying copyrighted papers into the repository without redistribution rights.
