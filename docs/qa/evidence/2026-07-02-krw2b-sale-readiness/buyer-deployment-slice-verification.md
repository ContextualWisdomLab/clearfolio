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
| License policy | `python3 scripts/check_sbom_license_policy.py --sbom docs/qa/evidence/2026-07-02-krw2b-sale-readiness/sbom-cyclonedx.json --policy docs/security/2026-07-02-license-policy.json` | Pass in engineering-review mode: 136 allowed, 6 review-required, 0 violations. |
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
