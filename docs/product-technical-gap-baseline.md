# Clearfolio Product and Technical Gap Baseline

## Goal and loop

Clearfolio is the ContextualWisdomLab document-viewer bounded context. Its buyer
quality loop is review → root-cause repair → exact-head validation → ordinary
merge. A queued, skipped, stale-head, or fail-closed Check is not passing.

## Current product and technical boundary

- **Product responsibility:** submit documents asynchronously, expose conversion
  status, render same-origin PDF artifacts, and enforce tenant-scoped access.
- **Runtime boundary:** Spring WebFlux controllers delegate validation,
  conversion, state, artifact, authorization, and audit work to their owning
  services and repositories; no cross-service database access is authorized.
- **Supply-chain boundary:** Maven dependency policy, CycloneDX SBOM, license
  policy, third-party attribution, Trivy, OSV, Semgrep, and CodeQL provide
  fail-closed release evidence.

## PRD and TRD acceptance baseline

| Evidence | Acceptance rule | Status |
| --- | --- | --- |
| Product behavior | `mvn -B --no-transfer-progress verify`; zero test failures, errors, or skips; production line and branch coverage 100% | Required on exact repair head |
| Public contract | Java 21 compilation and public Javadocs complete with warning and deprecation budget 0 | Required on exact repair head |
| Supply chain | Trivy/OSV/SAST report no actionable finding; committed SBOM and attribution match the resolved dependency graph | Repair in progress on #503 |
| Review admission | Independent approval and all required terminal Checks bind to the current head | Not yet satisfied |

## Gap and action ledger

| Gap | Exact evidence and RCA | Action | Status |
| --- | --- | --- | --- |
| Jackson denial-of-service exposure | clearfolio#660 head `2a6b0b621e075da224210c0ca78db344af5dea52`, Security run `36778995059`, Trivy job `110104143035`: CVE-2026-91776 and CVE-2026-91777 in `jackson-databind` 2.22.2 | Repair canonical dependency owner #503 with Jackson 2.22.3, a downgrade regression test, refreshed SBOM/attribution, and removal of the expired OSV exception | In progress |
| Historical evidence drift | #503 review found its original one-file 2.22.2 bump left SBOM, attribution, POM rationale, and expired ignore stale | Keep dependency version and buyer evidence in one PR; run attribution/license drift checks | In progress |
| Merge admission | #503 is Draft and has unresolved review findings; #660 is Ready but fails Security | Keep both deltas open; do not merge until #503 is GREEN and independently reviewed, then ordinarily restack consumers | Hold |

## Decision traceability

The selected repair is the upstream Jackson 2.22.3 patch line, released
2026-09-21 with fixes for both CVEs. Ignoring the findings, weakening Trivy, or
retaining the expired OSV exception was rejected because each would preserve a
buyer-visible denial-of-service risk or conceal stale evidence. A new duplicate
dependency PR was rejected because #503 already owns the Jackson version delta.

## References

FasterXML. (2026, September 21). *Jackson release 2.22.3*.
https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.22.3
