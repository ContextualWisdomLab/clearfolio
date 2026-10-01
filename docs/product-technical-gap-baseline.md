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
| Supply chain | Trivy/OSV/SAST report no actionable finding; committed SBOM and attribution match the resolved dependency graph | #503 Security `36824043955` and SAST `36824043968` GREEN |
| Review admission | Independent approval and all required terminal Checks bind to the current head | Not yet satisfied |

## Gap and action ledger

| Gap | Exact evidence and RCA | Action | Status |
| --- | --- | --- | --- |
| Jackson denial-of-service exposure | clearfolio#660 head `2a6b0b621e075da224210c0ca78db344af5dea52`, Security run `36778995059`, Trivy job `110104143035`: CVE-2026-91776 and CVE-2026-91777 in `jackson-databind` 2.22.2 | Repair canonical dependency owner #503 with Jackson 2.22.3, a downgrade regression test, refreshed SBOM/attribution, and removal of the expired OSV exception | Resolved at source commit `3e9ba61e1aa2a903e920cf832f9daa5467f2e166`; CI `36824043863`, Security `36824043955`, SAST `36824043968`, and fuzz `36824043977` GREEN |
| Historical evidence drift | #503 review found its original one-file 2.22.2 bump left SBOM, attribution, POM rationale, and expired ignore stale | Keep dependency version and buyer evidence in one PR; run attribution/license drift checks | Resolved; both review threads closed and local evidence contracts GREEN |
| #661 consumer security integration | #661 predecessor `600995328dc03d53a63e7b67d3b8c9501f3baa17`, Security `36798597036`, Trivy `110167652487`, reported CVE-2026-68497, CVE-2026-91776, CVE-2026-91777, CVE-2026-19032, and CVE-2026-83557 in Jackson 2.22.1 | Ordinary-integrate the complete #503 owner set while preserving the required-field accessibility controller and test | Integrated in tree `83d7f78f405f317b6c9beabf5a3aed1cff889485`; Security `36829111902`, CI `36829111991`, SAST `36829111823`, and fuzz `36829111945` GREEN on predecessor integration head |
| Repeated concurrent owner rollback on #661 | Commits `5d27441771689395358109d735e16d2ea9bbe30e` and `19795e9f645a6a731ad3db869ec441d58f7d9786` each reverted the same eight owner paths to Jackson 2.22.1, restored the expired OSV exception, deleted this baseline, and removed downgrade contracts. Security runs `36829505965` and `36829843477` failed; Trivy job `110262678487` reproduced all five findings. | Preserve both commits in ordinary history; restore the exact #503 blob set in follow-ups `a0cfe28fb971963d6fa03075f50b86a3ea54bd69` and `020ba5fd789dd45c7b985657e994e09ab04148ab`; move the unstable PR to Draft and require a fresh exact-head validation cycle | Owner tree restored; #661 remains Draft and merge HOLD. Cancelled fuzz/CodeQL runs are not passing evidence. |
| Merge admission | #503 is Ready after product/security/fuzz validation; its Draft CodeQL run `36824043888` was skipped. #661 is Draft after repeated concurrent rollback. | Obtain non-cancelled exact-head fuzz and CodeQL verdicts plus independent approval after the branch is stable, then ordinarily integrate | Hold |

## Decision traceability

The selected repair is the upstream Jackson 2.22.3 patch line, released
2026-09-21 with fixes for both CVEs. Ignoring the findings, weakening Trivy, or
retaining the expired OSV exception was rejected because each would preserve a
buyer-visible denial-of-service risk or conceal stale evidence. A new duplicate
dependency PR was rejected because #503 already owns the Jackson version delta.
Concurrent commits are preserved in ordinary history; only their proven
security-owner rollback is counteracted, and the PR remains Draft until its
single-writer and exact-head evidence are stable.

## References

FasterXML. (2026, September 21). *Jackson release 2.22.3*.
https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.22.3


## Successor handoff — 2026-10-01

The original #661 writer repeated the identical eight-path Jackson downgrade five
times at `5d27441771689395358109d735e16d2ea9bbe30e`,
`19795e9f645a6a731ad3db869ec441d58f7d9786`,
`980503850cff4f4a5e0d6bbf7e11b51e609f528f`,
`a486ce3e001f312f8b60d70ecbdc572c5336d11c`, and
`586bfc9a4a3436653e0488cae55fa4b34b22e525`. Direct repair on that
branch could not remain authoritative. Ready successor #662 at
`78587632e979de03895d9a9e49ad86bd0a3f85db` carries every valid accessibility
and canonical-owner delta, preserves all five concurrent commits in ordinary
history, and restores tree `41b7c5a757c449769bfacfa67ccfc27f05e37bb9`.
The predecessor remains open Draft as evidence. Successor #662 is Ready only
for review admission; merge remains HOLD until exact-current-head Checks are
terminal and an independent approval exists.


## #663 carryover — 2026-10-02

Draft #663 exact `2b49634cfabc00d888ef2e14a23a865f5d4c5f85` independently
reproduced the Jackson 2.22.1 scanner failure and repaired its POM to 2.22.3, but
that partial repair duplicated canonical owner #503 and omitted its regenerated
SBOM, attribution, expired-exception removal, and cross-artifact drift contract.
Those owner paths remain authoritative in successor #662.

The #663 production UI blob
`d55dfd56f8a9709b999b0c722d5dc75d4f13d479` is byte-identical to #662.
Its stricter response regression, which asserts the complete label and span
structure, is carried forward here. The standalone string-search dependency test
is superseded by `DependencyPolicyTest`, which parses the POM and binds Jackson
2.22.3 to the SBOM and attribution evidence. #663 remains open until this
successor head receives exact-head verification; retirement is permitted only
after that complete carryover is verified.
