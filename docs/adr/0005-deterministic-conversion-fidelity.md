# ADR-0005: Separate deterministic supported conversion from demo placeholder output

Status: Accepted
Implementation maturity: protected-main PDF passthrough `IMPLEMENTED_ON_MAIN`; non-PDF production conversion `PLANNED`

## Context and drivers

Protected main can pass through validated PDFs and can generate a placeholder one-page PDF for non-PDF sources. Calling that placeholder a DOCX/HWP/Office conversion would create a false fidelity claim and an unsafe release boundary. Enterprise buyers need reproducible evidence for what survives conversion.

Office conversion engines also process highly complex attacker-controlled formats through native and parser-heavy runtimes. A converter crash, memory leak, parser exploit, macro/active-content behavior, or external-resource fetch must not inherit the Clearfolio API process/container's filesystem, credentials, network namespace, or tenant authority merely because an adapter library can start an office process locally.

## Alternatives

1. Treat any generated PDF as a successful conversion.
2. Use an LLM or visual opinion as the primary fidelity oracle.
3. Classify output as passthrough/transformed/degraded/unsupported/development-placeholder and allow supported-format claims only after deterministic real-fixture acceptance, while running production Office conversion behind an isolated provider-neutral adapter boundary.
4. Embed/start the Office runtime inside the Clearfolio API process/container for operational simplicity.

## Decision

Choose alternative 3 and reject alternative 4 for production Office transformation.

Deterministic converter adapters are the production authority. No uploaded macro/active content is executed, external resource fetching is disabled by default, and unsupported or unverifiable inputs fail closed. LLMs may assist documentation or investigation but do not decide conversion correctness.

The Clearfolio application depends on a versioned provider-neutral `office_conversion_adapter` contract. A production Office engine is owned by a separately isolated execution boundary, such as a sandboxed sidecar/external process service or separately operated authenticated remote converter. The Office runtime does **not** run inside the Clearfolio API-container trust boundary and does not inherit application secrets or unrestricted network/filesystem authority.

JODConverter, LibreOffice, Collabora, or another engine may implement that adapter after qualification; none is architectural authority by itself. JODConverter's support for a local manager is a library capability, not approval to start LibreOffice inside the API container. Exact adapter/runtime versions, images, fonts, codecs, dictionaries and licenses are pinned and reviewed as release materials rather than floated to a moving “latest” version.

## Consequences

The supported-format list grows more slowly and deployment gains an explicit converter boundary, but product claims and blast-radius controls become defensible. Realistic Office/PDF fixtures, expected outcomes, converter/version/configuration provenance and sandbox policy become release artifacts.

The service interface remains replaceable for standalone and naruon/MSA composition. Hosts integrate through Clearfolio's versioned API/service boundary rather than gaining direct access to converter temporary storage or Clearfolio persistence.

## Failure and recovery

A converter crash, timeout, malformed container, unsupported construct, missing required renderer dependency, sandbox exhaustion, network-policy violation or fidelity-gate failure produces controlled failure evidence; it never silently returns a placeholder as a supported conversion.

The current public lifecycle remains `SUBMITTED`, `PROCESSING`, `SUCCEEDED`, and `FAILED`. **Degraded conversion evidence is a reason/profile attached to the public `FAILED` terminal state, not a fifth terminal state.** A degraded result cannot create a supported-format claim and cannot be published as a successful converted artifact. Introducing a separate public degraded state would require an explicit versioned API change, migration note, client/consumer contract tests, and corresponding state-machine/release updates.

Cancellation/timeout must terminate or quarantine the exact converter process/job generation. A later job must not inherit its temporary profile or files. Retry is bounded and limited to explicit transient categories; unsupported, policy-denied, password-protected or structurally rejected inputs are not retried as transient failures.

Converter/runtime rollback follows the realistic fixture and provenance contract in `docs/FIDELITY_ACCEPTANCE.md` and `docs/MIGRATION_ROLLBACK.md`; an insecure old converter is not restored solely for output compatibility.

## Security and privacy

Production converter execution uses bounded CPU/memory/time/process count/temp storage, a non-root identity, least filesystem permissions, no inherited API/cloud credentials, no Docker/host control socket, and deny-by-default outbound network access. Seccomp/AppArmor/SELinux or equivalent platform controls are applied where supported.

Inputs are validated before conversion for extension/MIME/container policy, active content, encryption/password state, malformed structures, archive depth/decompression expansion and configured size/object limits. Outputs are published only after PDF/media-type/size/page/integrity validation and applicable content/security checks.

No unreviewed macros/scripts execute. No implicit external links/resources are followed. Temporary files are app-owned to the converter boundary, bounded, and cleaned after success, failure, cancellation or crash. Fixtures used in public CI must be redistributable and free of confidential data.

## Supply-chain and license boundary

Adapter and runtime qualification are separate:

- Java adapter license compatibility does not prove the complete Office runtime/container is redistributable or secure;
- generate and verify SBOM/provenance/attribution for the exact converter runtime and the Clearfolio client independently;
- pin images by digest and dependencies by reviewed version/BOM;
- record the exact office runtime, fonts, codecs, dictionaries and native packages that affect output;
- document vulnerability response, rebuild/rollback and emergency converter-disable procedures.

Issue #5 is the executable product qualification record. `docs/RESEARCH_TRACEABILITY.md` records the current official JODConverter manager/configuration sources and LibreOffice licensing source without turning a transient current version into a timeless pin.

## Compatibility and migration

Existing PDF passthrough remains compatible. Non-PDF placeholder behavior remains labelled development/demo until replaced. New converter adapters are introduced through a versioned support matrix and fixture suite.

Moving from placeholder behavior to transformed Office support is a product-contract expansion, not a transparent implementation swap: API/UI/support documentation must expose unsupported/degraded/failure semantics accurately during migration.

## Tests and acceptance

For each claimed format require:

- authorized/redistributable realistic fixtures;
- structure/text/images/tables/fonts/page expectations and controlled visual comparison;
- macro/active-content/external-link/password/malformed/container-expansion security fixtures;
- deterministic or explicitly bounded output/provenance comparison;
- sandbox no-secret/no-network/no-host-control assertions;
- CPU/memory/time/process/temp-storage pressure tests;
- cancellation, crash, cleanup, process-recycle, retry-category and stale-generation tests;
- degraded-output tests proving `FAILED`, no fifth public state and no supported-format claim;
- license/SBOM/provenance evidence for the exact adapter/runtime image;
- accessibility/print/export checks where user-visible;
- exact-head CI/security/fuzz/review and integrated release acceptance.

A successful converter process exit alone is never a production support oracle.

## Rollback / supersession

A converter version may be rolled back only to a release that still satisfies current security, compatibility and fixture requirements. Rollback must not reintroduce a known sandbox or authorization weakness. Supersede this ADR only if a new authority can provide equal or stronger deterministic fidelity, isolation, privacy, supply-chain and recovery evidence.
