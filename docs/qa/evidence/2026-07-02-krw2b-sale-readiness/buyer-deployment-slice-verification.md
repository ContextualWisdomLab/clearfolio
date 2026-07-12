# Buyer Deployment Slice Verification

Date: 2026-07-02T21:21:06+0900

Source head before this supplemental verification:
`63d659b08b0a534e9059ff487f412e62840aa167`

This supplemental evidence covers the buyer deployment integration slice:
`src/main/resources/application-buyer-demo.yml`,
`docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md`, and the
related diligence, plan, FigJam handoff, README, and evidence-index updates.

## Runtime Note

The active shell for this verification exposed Java 26.0.1. Java 21 was not
available from `/usr/libexec/java_home -V` in this environment. The earlier
release evidence in this folder remains the Java 21 evidence baseline; this file
is a supplemental verification for the added buyer deployment slice.

```text
java version "26.0.1" 2026-04-21
Java(TM) SE Runtime Environment (build 26.0.1+8-34)
Java HotSpot(TM) 64-Bit Server VM (build 26.0.1+8-34, mixed mode, sharing)
```

## Commands and Results

| Gate | Command | Result |
| --- | --- | --- |
| Git whitespace | `git diff --check` | Pass, no output. |
| Markdown lint | `npx markdownlint-cli2 README.md docs/deployment/2026-07-02-buyer-deployment-integration-playbook.md docs/plans/2026-07-02-krw2b-sale-readiness-execution-plan.md docs/diligence/2026-07-02-buyer-diligence-index.md docs/design/2026-07-02-buyer-demo-kpi-figjam-handoff.md docs/qa/evidence/2026-07-02-krw2b-sale-readiness/README.md docs/qa/evidence/LATEST.md` | Pass, 7 files, 0 errors. |
| Compile | `mvn -DskipTests compile` | Pass, build success, no compile warnings in output. |
| Tests | `mvn test` | Pass, 312 tests, 0 failures, 0 errors, 0 skipped. |
| Coverage | `awk -F, ... target/site/jacoco/jacoco.csv` | Pass, `missed_instr=0 missed_branch=0 missed_line=0`. |
| JavaDoc | `mvn -q -DskipTests javadoc:javadoc` | Pass, no output. |
| SAST | `uvx semgrep --config p/java --metrics=off --error --json --output /tmp/clearfolio-buyer-deployment-semgrep.json src/main/java src/test/java` | Pass, 60 Java rules, 50 tracked files, 0 findings, 0 errors. |
| License policy | `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json --require-no-review` | Pass in buyer-release mode: 61 allowed, 0 review-required, 0 violations. |
| Buyer-demo profile smoke | `SPRING_PROFILES_ACTIVE=buyer-demo ... mvn -q spring-boot:run -Dspring-boot.run.arguments="--server.port=18084"` with `/healthz` and `/` curl checks | Pass: `health={"status":"ok"}`, root contains Clearfolio copy and recovery panel markup. |

## FigJam Evidence

Added `Clearfolio Buyer Integration Deployment Flow` to:
<https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM>

Figma Code Connect was not used.

## Review Thread Follow-Up

Date: 2026-07-02T21:24:59+0900

Source head before this follow-up:
`2a6a0907ba87e409e594bfaa42bd48933635ed15`

Unresolved Copilot review comments were not treated as a process blocker, but
three small test-clarity comments were actionable and low risk:

- renamed `HealthControllerTest` method so it no longer claims HTTP routing
  coverage when it directly invokes the controller method;
- added `details.maxUploadSize=1024` assertion to the reactive codec limit test
  so it cannot pass through the service validation path by accident;
- asserted `ThreadPoolTaskExecutor` type before casting in
  `ConversionExecutorConfigTest`.

Verification after the follow-up:

| Gate | Result |
| --- | --- |
| Targeted tests | `mvn -Dtest=HealthControllerTest,ConversionControllerMultipartLimitTest,ConversionExecutorConfigTest test` passed, 11 tests, 0 failures, 0 errors. |
| Compile | `mvn -DskipTests compile` passed, no compile warnings in output. |
| Markdown lint | `npx markdownlint-cli2 ...` passed, 8 files, 0 errors. |
| JavaDoc | `mvn -q -DskipTests javadoc:javadoc` passed, no output. |
| SAST | Semgrep passed, 60 Java rules, 50 tracked files, 0 findings, 0 errors. |
| Full tests and coverage | `mvn test` passed, 312 tests, 0 failures, 0 errors; `missed_instr=0 missed_branch=0 missed_line=0`. |

## Connector Seed Verification

The repository now includes
`docs/deployment/clearfolio-buyer-connector.openapi.yaml` as an OpenAPI 3.0
import seed for a buyer-owned gateway or Power Platform custom connector.

Validation:

| Gate | Result |
| --- | --- |
| YAML parse | `ruby -e 'require "yaml"; YAML.load_file("docs/deployment/clearfolio-buyer-connector.openapi.yaml")'` passed. |
| OpenAPI lint | `npx @redocly/cli lint docs/deployment/clearfolio-buyer-connector.openapi.yaml` passed with no warnings or errors. |
| Markdown lint | `npx markdownlint-cli2 ...` passed for changed Markdown files; OpenAPI YAML is validated by the OpenAPI linter, not Markdown lint. |

Claim boundary:

- The connector seed reflects current Clearfolio JSON APIs and signed tenant
  headers.
- It is not a buyer-tenant-imported connector package yet.
- It is not a production OIDC/JWT deployment profile.

## Third-Party Attribution Verification

Date: 2026-07-03T08:12:32+0900

Source head before this attribution slice:
`6cfdcc0e3cf8b9076b5cea2f01277c56772c3671`

This slice adds a buyer data-room attribution package generated from the current
CycloneDX SBOM, plus a drift check that fails when the generated file no longer
matches the SBOM.

Validation:

| Gate | Result |
| --- | --- |
| TDD red check | `python3 scripts/test_render_third_party_attribution.py` first failed because `render_third_party_attribution` did not exist. |
| Targeted renderer tests | `python3 scripts/test_render_third_party_attribution.py` passed, 2 tests. |
| Attribution render | `python3 scripts/render_third_party_attribution.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --output docs/legal/2026-07-03-third-party-attribution.md` generated the current data-room notice. |
| Attribution drift check | `python3 scripts/render_third_party_attribution.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --output docs/legal/2026-07-03-third-party-attribution.md --check` passed and is recorded in `third-party-attribution-check.log`. |

Claim boundary:

- The attribution package is engineering-generated evidence, not legal advice.
- Final legal release review is still required before signed sale or enterprise
  redistribution.
- No separate library, submodule, or new dependency was added; the renderer uses
  only the Python standard library.

## Production Auth Readiness Verification

Date: 2026-07-03T08:20:12+0900

Source head before this production-auth slice:
`947f26b68c60e4b233b3728ff61a46ab1639388a`

This slice prevents the current gateway-signed tenant-header scaffold from being
misrepresented as production auth when the signing secret is absent. It does not
implement OIDC/JWT; it makes the production profile fail closed until signed
tenant claims are configured.

Validation:

| Gate | Result |
| --- | --- |
| TDD red check | `mvn -Dtest=ProductionAuthReadinessConfigTest test` first failed because `ProductionAuthReadinessConfig` did not exist. |
| Targeted production-auth tests | `mvn -Dtest=ProductionAuthReadinessConfigTest test` passed, 2 tests, 0 failures, 0 errors. |

Claim boundary:

- `SPRING_PROFILES_ACTIVE=production` now requires
  `clearfolio.tenant-claims.hmac-secret`.
- The local buyer-demo profile can still run unsigned for screenshots and
  design evidence.
- Validated OIDC/JWT issuer, audience, expiry, key rotation, and role mapping
  remain the next production-auth implementation gap.

## Buyer Data-Room Manifest Verification

Date: 2026-07-03T08:27:38+0900

Source head before this manifest slice:
`69b1dfc6b60aeb0df805ae45e15392647f1cbec5`

This slice adds a buyer data-room manifest so Product Design, Figma, Data
Analytics, security, deployment, and QA evidence can be inspected as one
package instead of a loose list of links. It is intentionally in-repo and uses a
standard-library checker; no library split, submodule, or new dependency was
added.

Validation:

| Gate | Result |
| --- | --- |
| TDD red check | `python3 scripts/test_check_buyer_dataroom_manifest.py` first failed because `check_buyer_dataroom_manifest` did not exist. |
| Targeted manifest tests | `python3 scripts/test_check_buyer_dataroom_manifest.py` passed, 4 tests. |
| Manifest check | `python3 scripts/check_buyer_dataroom_manifest.py --manifest docs/diligence/2026-07-03-buyer-data-room-manifest.json` passed with 19 artifacts, 7 gates, and 0 errors. |

Claim boundary:

- The manifest proves evidence package completeness, not buyer legal approval.
- External URLs such as GitHub PR #82 and the Figma FigJam board are checked for
  URL shape, while live availability remains an external service concern.

## Durable Job Repository Design Verification

Date: 2026-07-02T21:39:41+0900

Source head before this design slice:
`0555b8e5e86fce57e647074844e8312f85d55f92`

This slice adds a durable conversion job repository migration plan and links it
from the architecture, diligence, sale-readiness plan, FigJam handoff, README,
and latest evidence index.

Validation:

| Gate | Result |
| --- | --- |
| Git whitespace | `git diff --check` passed with no output. |
| Markdown lint | `npx markdownlint-cli2 ... docs/persistence/2026-07-02-durable-conversion-job-repository-plan.md` passed, 8 files, 0 errors. |
| Compile | `mvn -DskipTests compile` passed, build success, no compile warnings in output. |
| Tests and coverage | `mvn test` passed, 312 tests, 0 failures, 0 errors; JaCoCo remained `missed_instr=0 missed_branch=0 missed_line=0`. |
| JavaDoc | `mvn -q -DskipTests javadoc:javadoc` passed, no output. |
| SAST | `uvx semgrep --config p/java --metrics=off --error --json --output /tmp/clearfolio-durable-repository-semgrep.json src/main/java src/test/java` passed, 60 Java rules, 50 tracked files, 0 findings. |
| License policy | `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json --require-no-review` passed in buyer-release mode: 61 allowed, 0 review-required, 0 violations. |

FigJam evidence:

- Added `Clearfolio Durable Job Repository Target Architecture` to:
  <https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM>
- Figma Code Connect was not used.

Claim boundary:

- The durable repository package is a design and migration artifact, not a
  completed SQL repository implementation.
- It records the decision to keep the first implementation in-repo and avoid a
  premature library, submodule, or dependency split.
- It identifies explicit lifecycle transition persistence as a prerequisite
  before claiming production-grade durable job recovery.

## Conversion Job State Store Implementation Verification

Date: 2026-07-02T21:57:11+0900

Source head before this implementation slice:
`e9607c0cdc8231458b30dfbf07eec2a1d8b6cfa7`

This slice implements the first pre-SQL durable-persistence prerequisite:
`ConversionJobStateStore` now owns conversion lifecycle transitions for worker
claims, retry scheduling, success, dead-lettering, and operator retry
acceptance. The default runtime is still in-memory; no SQL dependency,
submodule, or separate library was added.

Validation:

| Gate | Result |
| --- | --- |
| TDD red checks | Repository test first failed because `ConversionJobStateStore` did not exist; worker test then failed because the state-store constructor path did not exist; document service retry test then failed because explicit state-store injection did not exist. |
| Targeted tests | `mvn -Dtest=InMemoryConversionJobRepositoryTest,RepositoryBackedConversionJobStateStoreTest,DefaultConversionWorkerTest,DefaultDocumentConversionServiceTest test` passed, 66 tests, 0 failures, 0 errors. |
| Git whitespace | `git diff --check` passed with no output. |
| Markdown lint | `npx markdownlint-cli2 ... docs/superpowers/plans/2026-07-02-conversion-job-state-store.md` passed, 9 files, 0 errors. |
| Compile | `mvn -DskipTests compile` passed, build success, no compile warnings in output. |
| Tests and coverage | `mvn test` passed, 327 tests, 0 failures, 0 errors; JaCoCo remained `missed_instr=0 missed_branch=0 missed_line=0`. |
| JavaDoc | `mvn -q -DskipTests javadoc:javadoc` passed, no output. |
| SAST | `uvx semgrep --config p/java --metrics=off --error --json --output /tmp/clearfolio-state-store-semgrep.json src/main/java src/test/java` passed, 60 Java rules, 52 tracked files, 0 findings. |
| License policy | `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json --require-no-review` passed in buyer-release mode: 61 allowed, 0 review-required, 0 violations. |

FigJam evidence:

- Added `Clearfolio Conversion State Store Implementation Flow` to:
  <https://www.figma.com/board/114nJPcTcQzXvAEIS9T4gM>
- Figma Code Connect was not used.

Claim boundary:

- The lifecycle transition boundary is implemented in code and covered by
  repository, worker, adapter, and operator retry tests.
- The implementation remains process-local with `InMemoryConversionJobRepository`.
- Production-grade persistence still requires SQL state, append-only lifecycle
  events, restart recovery tests, and buyer-sandbox activation.
