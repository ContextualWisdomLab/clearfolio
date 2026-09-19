# Security Policy

## Supported Version and Evidence Boundary

Clearfolio is maintained on the protected `main` branch. Security fixes are
accepted through reviewed pull requests and are not treated as shipped until
they integrate through repository protection. Active PRs may document proposed
or implemented-on-branch controls, but protected `main` remains the authority
for deployed product behavior.

Clearfolio is a document-conversion and viewing service. Uploaded documents,
document-derived strings, browser data, filenames, PDF actions, remote-resource
references, model output, and pull-request text are untrusted input. The
non-PDF development/demo placeholder on protected main is not production Office
conversion or fidelity evidence. Production transformed-format support requires
the qualification gates in `docs/FIDELITY_ACCEPTANCE.md` and issue #5.

## Reporting a Vulnerability

Please report suspected vulnerabilities privately through GitHub Security
Advisories for this repository:
https://github.com/ContextualWisdomLab/clearfolio/security/advisories/new.

Include the affected endpoint or component, the input and preconditions, the
observed impact, and a minimal reproduction when possible. Do not place customer
document content, raw credentials, signed tokens, private tenant identifiers, or
other sensitive evidence in a public issue.

For automation failures, include the failing check name, run URL, exact source
revision when known, and the package/CVE or SARIF rule that triggered the alert.
Reports involving document parsing, artifact delivery, tenant boundaries,
conversion runtimes, or dependency resolution are treated as high-sensitivity
until triage proves otherwise.

## Authorization and Artifact Delivery

Tenant authorization and signed artifact delivery are separate authorities.
Protected document-byte flows must preserve same-tenant concealment and the
least privilege required by the route. Where the signed artifact contract
applies, a request must also pass the canonical signature, expiry, scope,
document/tenant/checksum binding, issued-ledger, revocation, single-range, and
controlled read-audit checks defined by the current implementation and
`docs/API_CONTRACT.md`.

A generic tenant permission does not replace the signed artifact boundary. The
signed artifact-link route exists on protected main. Direct conversion-job
download consistency is being hardened in active PR #270; until that work is
integrated, its stronger direct-download behavior is `ACTIVE_PR`, not shipped
truth.

Cross-tenant and missing-resource cases must remain non-enumerating where the
API contract requires concealment. Public error payloads and logs must not expose
local filesystem paths, document content, raw signed claims, raw artifact tokens,
or exception-controlled sensitive values.

## Audit Pseudonymization and Key Separation

Privacy-safe audit evidence uses purpose-bound, domain-separated HMAC
pseudonymization where that control is implemented. Active PR #270 strengthens
this boundary with separate policy-signing and audit-pseudonym purposes and
fail-closed key configuration. Do not reuse one key across those purposes.

Pseudonymized identifiers remain personal data when they can still single out or
be related back to a person or tenant. They therefore remain subject to
purpose limitation, least privilege, retention, key governance, and access
auditing; pseudonymization is not anonymization and is not permission for
unbounded reuse.

Key material must never be committed to source control or copied into logs,
error responses, documentation examples, model prompts, or build artifacts.
Current detailed key requirements and maturity are maintained in
`docs/adr/0003-audit-pseudonymization-key-separation.md` and the current reviewed
security implementation.

## Document Conversion Trust Boundary

Clearfolio must not execute document macros or active content as application
authority, and production conversion must not depend on following arbitrary
external document links. A future Office runtime must remain outside the
Clearfolio API-container trust boundary behind the provider-neutral conversion
contract, with bounded resources, isolated temporary storage, no inherited
application/cloud credentials, deny-by-default outbound network access, and
explicit source/output policy checks.

PDF output acceptance distinguishes inert fidelity-preserving navigation from
executable behavior. Supported URI/internal-link fidelity may be retained only
under the explicit versioned output policy; script, launch, submission/import,
embedded-file, chained active-action, or equivalent executable behavior fails
closed according to that policy.

## Dependency, CI, and Supply-Chain Expectations

Medium, high, and critical dependency advisories are remediated by updating the
affected package or its managing BOM/parent and re-running the exact applicable
security and test evidence. Workflow or repository-governance findings are not
dismissed silently; logs and review evidence must preserve the reason when a
gate cannot complete.

Release evidence must remain bound to the exact protected source and artifact
identity and include the applicable CI/security gates, exact owned production
coverage, public Javadocs, realistic fidelity and recovery evidence, SBOM and
third-party attribution, provenance/reproducibility, migration/rollback where
applicable, and required independent review. See
`docs/RELEASE_ACCEPTANCE.md` for the canonical integrated release gate.

## Security Documentation Map

- `docs/THREAT_MODEL.md` — trust boundaries, abuse cases, mitigations, and
  residual risks.
- `docs/API_CONTRACT.md` — route-level authority and controlled failure
  semantics.
- `docs/adr/0002-tenant-artifact-authorization.md` — tenant permission versus
  artifact-delivery authority.
- `docs/adr/0003-audit-pseudonymization-key-separation.md` — HMAC purpose/key
  separation and privacy treatment.
- `docs/adr/0005-deterministic-conversion-fidelity.md` — converter isolation,
  active-content policy, and fidelity truth.
- `docs/FIDELITY_ACCEPTANCE.md` — realistic transformed-format qualification.
- `docs/MIGRATION_ROLLBACK.md` — security-preserving migration and recovery.
- `docs/RELEASE_ACCEPTANCE.md` — exact integrated release evidence.
