# Fuzzing

Coverage-guided fuzzing of the viewer's untrusted-input parsers, using
[Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) (Apache-2.0)
integrated with JUnit 5 (`jazzer-junit`).

## Research basis

The seed-corpus and bounded coverage-guided strategy is grounded in Marcel
Böhme, Van-Thuan Pham, and Abhik Roychoudhury, “Coverage-based Greybox
Fuzzing as Markov Chain,” *Proceedings of the 2016 ACM SIGSAC Conference on
Computer and Communications Security (CCS '16)*, pp. 1032–1043,
doi:10.1145/2976749.2978428. The preserved source PDF is
`docs/papers/boehme-2016-coverage-based-greybox-fuzzing-as-markov-chain.pdf`
(SHA-256 `fb3a7a74280659f86f83a934cae7bd35660e4c699dbd1dbd6825834bfe132151`).
Its core result motivates retaining low-frequency-path crash seeds as durable
regressions while giving scheduled fuzzing a longer exploration budget than
per-PR fuzzing.

## Targets

The surfaces were selected with CodeGraph as the highest-value untrusted-input
boundaries (attacker-controlled tokens, filenames, and request headers). Each
harness asserts that arbitrary input only ever produces the *documented*
failure mode -- never an unhandled runtime exception.

| Harness | Surface under test | Invariant |
| --- | --- | --- |
| `ArtifactTokenParserFuzzTest` | `ArtifactLinkService.verifyReadToken` -- splits, HMAC-verifies, Base64URL-decodes and parses 10 token fields (UUID, epoch `Instant`s). Forges valid signatures to reach the deep parse path. | Only `ArtifactTokenException` (a mapped 4xx) escapes. |
| `DocumentValidationFuzzTest` | `DefaultDocumentValidationService.validateOrThrow` -- filename/extension parsing + policy-override headers. | Only `IllegalArgumentException` (incl. `UnsupportedDocumentFormatException`) escapes. |
| `TenantClaimsFuzzTest` | `TenantContext.fromHeaders` + `TenantAccessService.require` -- tenant/subject/permission headers and the signed-claims timestamp/signature. | `fromHeaders` never throws; `require` only fails with `ResponseStatusException`. |

Seed corpora live under
`src/test/resources/com/clearfolio/viewer/fuzz/<HarnessName>Inputs/<method>/`.

## How it runs

- **Normal build (`mvn test`)**: the `@FuzzTest` methods run in *regression*
  mode -- they replay the seed corpus (and any committed crash reproducers) as
  fast, deterministic unit tests. No libFuzzer driver required, so this works on
  every developer platform and keeps the default build cheap.

- **Fuzzing mode (`JAZZER_FUZZ=1`)**: libFuzzer drives each target for the
  bounded budget declared on its `@FuzzTest(maxDuration = "60s")` annotation.

```bash
# Regression replay of all fuzz seeds (part of the normal suite):
mvn test -Dtest='*FuzzTest'

# Actively fuzz a single target for its bounded budget:
JAZZER_FUZZ=1 mvn test -Dtest='ArtifactTokenParserFuzzTest'
```

## CI

`.github/workflows/fuzz.yml` fuzzes each target for 60s on pull requests and
pushes to `main`, and for 10 minutes per target nightly. A newly discovered
crasher fails the job; the reproducer is uploaded as a build artifact. Commit
the reproducer into the harness's `Inputs/<method>/` directory to turn it into a
permanent regression test.

## Findings

The initial harness immediately surfaced an unhandled `java.time.DateTimeException`
in the artifact-token parser: `Instant.ofEpochSecond(Long.parseLong(...))` threw
on an out-of-range (but syntactically valid) epoch second, escaping the
`catch (IllegalArgumentException)` block and turning a would-be 401 into a 500.
Fixed by also catching `DateTimeException` in `ArtifactLinkService.parseAndVerify`.
