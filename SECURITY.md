# Security Policy

## Supported Version

Clearfolio is currently maintained on the `main` branch. Security fixes are
accepted for the active application and are released through reviewed pull
requests.

## Reporting a Vulnerability

Please report suspected vulnerabilities privately through GitHub Security
Advisories for this repository:
https://github.com/ContextualWisdomLab/clearfolio/security/advisories/new.
Include the affected endpoint, dependency, input, observed impact, and a
minimal reproduction when possible.

For automation failures, include the failing check name, run URL, and the
package/CVE or SARIF rule that triggered the alert. Reports that involve
document parsing, artifact links, tenant boundaries, or dependency resolution
are treated as high-sensitivity until triage proves otherwise.

## Remediation Expectations

Medium, high, and critical dependency advisories are fixed by bumping the
affected package or its managing BOM/parent. Workflow or repository-governance
findings are not dismissed silently; the relevant workflow logs must show the
reason when a check cannot complete.
