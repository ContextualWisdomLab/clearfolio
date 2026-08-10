# Hourly autonomous product development

## Purpose

Clearfolio separates organization PR maintenance from repository-local product development.

- `ContextualWisdomLab/.github` owns **one central PR-maintenance caller**, `clearfolio-hourly-review-repair.yml`. The central caller inventories open Clearfolio pull requests, invokes the shared review-repair engine, preserves the existing reviewer identities and credentials, and leaves approval and merge decisions to repository protection.
- `.github/workflows/hourly-product-development.yml` is the only repository-local hourly caller in this change. It runs at minute 23 and may propose one bounded buyer-visible increment while unrelated pull requests await checks or independent approval. It allows at most two same-repository `automation/hourly-` Drafts, rejects every path already owned by an open pull request, and requires protected `main` to remain unchanged through proposal, verification, and publication.

Keeping PR maintenance central prevents duplicate scheduled sweeps, conflicting branch updates, repeated review dispatches, and avoidable GitHub Actions consumption. The local product loop remains independently usable by Clearfolio while its published Draft PRs re-enter the same organization review and merge plane as every other change.

The central maintenance schedule becomes active only after its independently reviewed implementation is present on the protected default branch of the organization `.github` repository. The product schedule likewise becomes active only after this workflow reaches Clearfolio's protected default branch. A workflow file present only on a pull-request branch is not represented as operational automation.

## PR-maintenance trust boundary

The central caller and reusable engine own:

1. current open-PR inventory;
2. exact-head review and repair dispatch;
3. same-head retry throttling;
4. branch-update eligibility;
5. exact-head Check revalidation;
6. guarded direct or automatic merge eligibility.

Clearfolio does not copy that privileged implementation into the product repository. It does not rename or remap central reviewer credentials, synthesize approval, or add a second scheduler. The central plane cannot treat a comment, status, queued job, predecessor review, or stale approval as passing evidence. A qualifying independent approval, required current-head Checks, unresolved-thread policy, expected-head safety, and branch protection remain mandatory.

The product-development workflow does not call the review agents, approve itself, merge its own Draft, change branch protection, publish a release, or deploy. Its only write-capable outcome is a new bounded Draft pull request through a separately scoped publication identity.

## Product-maintainer identity and prerequisites

The product scheduler uses checksum-pinned OpenCode 1.18.13. The reviewed Linux x64 archive SHA-256 is `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937`.

Repository administrators must configure these values before a non-dry run can produce a proposal:

- organization or repository secret `NVIDIA_NIM_API_KEY`;
- repository variable `CLEARFOLIO_MAINTAINER_APP_CLIENT_ID`;
- repository secret `CLEARFOLIO_MAINTAINER_APP_PRIVATE_KEY`.

The workflow maps `NVIDIA_NIM_API_KEY` into the process-local `NVIDIA_API_KEY` variable expected by the pinned OpenCode NVIDIA provider. The value is masked, never written to source, and scanned against model output and every model-writable path. Missing credentials cause the workflow to fail or skip proposal creation safely. They never select an unreviewed provider, an unauthenticated model, or a broader repository token as model authentication.

The Maintainer App must be installed only for `ContextualWisdomLab/clearfolio` and should receive the minimum repository permissions needed to create a branch and Draft pull request. The token-minting action downscopes each publication token to `contents: write` and `pull-requests: write`. GitHub groups branch pushes and release APIs under `contents`, and PR creation and merge APIs under `pull-requests`; it does not offer a branch-only or Draft-only token category. Clearfolio therefore combines the narrowest available categories with immutable patch identity, a publication-only job, Draft-only commands, regression tests that prohibit merge and release commands, and protected-branch rules. The App must not receive administration, ruleset, secret, environment, security-event, deployment, workflow, or bypass authority.

## Three-stage trust separation

### 1. Credentialed proposal

The model runner has repository read access and a narrowly enumerated edit boundary:

- `src/main/**`;
- `src/test/**`;
- `docs/**`;
- `README.md`;
- `CHANGELOG.md`.

Workflow files, scripts, `pom.xml`, dependency locks, environment files, `.git/**`, external directories, web search, web fetch, nested tasks, interactive questions, skills, and unapproved shell commands are denied. Protecting build and dependency inputs prevents model output from changing executable supply-chain configuration before the next stage runs the proposed source.

OpenCode is invoked without `--auto`. An unmatched permission request in non-interactive execution is rejected instead of being silently granted. Explicitly allowed inspection and edit operations still work, while a new tool, an unexpected path, or a pattern mismatch fails closed.

The credentialed step does not execute model-modified repository code. It cannot run Maven, pytest, project modules, code generation, language servers, commits, pushes, or GitHub publication. It may inspect only bounded Git status and diff evidence. This prevents proposed code from reading the NVIDIA credential or runner credentials.

A proposal must remain reviewably bounded, test-first where behavior changes, and within 20 changed files and 200,000 patch bytes. Deletions, renames, symlinks, binaries, mode changes, workflow changes, script changes, dependency changes, build metadata, versions, releases, deployments, approvals, and merges are rejected. New text files inside the allowed source, test, and documentation boundary are represented with Git intent-to-add so the immutable patch and file budget include them. Git `numstat` rejects opaque binary payloads before any artifact is uploaded.

The proposal stage seals:

- protected base SHA;
- Git patch;
- patch SHA-256;
- changed-file and stat summaries;
- bounded OpenCode result.

No credential is stored in the proposal artifact.

### 2. Credential-free verifier

A fresh runner checks out protected `main`, downloads the immutable proposal, and rejects it when the bounded autonomous Draft queue is full, any proposed path is already changed by an open pull request, `main` moved, the patch hash changed, or the patch no longer applies. It runs `git apply --check` and `git diff --check`, then executes the repository acceptance commands:

```bash
mvn -B --no-transfer-progress verify
python -m pip install --disable-pip-version-check --no-cache-dir --require-hashes -r requirements-test.txt
python -m pytest -q scripts
```

This job receives neither the NVIDIA key nor the Maintainer App private key. It is the credential-free verifier for production tests, zero missed lines and branches, warning-free public Javadocs, packaging, deterministic buyer evidence, and workflow contracts.

The verifier still executes untrusted proposed tests and production code. Its Harden Runner policy therefore uses fail-closed blocked egress. The allowlist is limited to reviewed GitHub transport, Maven Central, and the PyPI hosts needed by the committed hash-locked test requirements. Proposed code cannot contact an arbitrary destination. Any new endpoint is a security-sensitive workflow-source change and must be reviewed narrowly rather than switching to observation-only network policy.

### 3. Publication-only identity

A third fresh runner rechecks the complete paginated PR inventory, same-repository autonomous Draft count, open-PR path ownership, protected-base SHA, and patch SHA-256. Only then does it mint a short-lived repository-scoped Maintainer App token, apply the already verified patch, push a unique automation branch, and open a Draft pull request.

The publisher's egress policy is also fail-closed. Its allowlist contains only the GitHub endpoints required to download verified evidence, mint the scoped token, push the branch, and create the Draft. NVIDIA, Maven Central, PyPI, and arbitrary destinations are absent.

The publication job does not execute proposed code and has no approval, automatic merge, merge, release, package publication, or deployment command. The resulting Draft enters ordinary exact-head CI, Security Scan, SAST, fuzzing, CodeRabbit, OpenCode/Noema/Strix, unresolved-thread, independent approval, repository-policy, and branch-protection processing.

## RCA-to-action contract

A wait state or failed attempt is evidence to analyze, not a terminal product outcome. Before editing, the hourly product loop identifies the observable symptom, traces a repository-controlled root cause or a clearly external boundary, and compares credible remedies.

Each candidate is checked against the permissions available in the current run, the allowed file boundary, open-PR path ownership, required credentials and dependencies, bounded execution cost, test-first reproducibility, security and privacy, rollback, and whether the credential-free verifier can prove the result. The loop executes the highest-impact feasible remedy immediately. When the original obstacle is external or infeasible, it continues with the next path-disjoint buyer-visible gap instead of ending with a blocker report or a request for human action.

The only valid no-change result is one backed by evidence that queue capacity, declared credentials, every safe material candidate, or repository ownership makes a write unrealistic in that run. The workflow records that deterministic reason without inventing secrets, weakening protection, racing another writer, or claiming an action that it could not execute.

## Work-conserving continuation

One bounded slice is a review and publication unit, not a run-completion condition. A completed mutation, Draft proposal, documentation update, review request, green verification, or proved blocker is intermediate while another safe action exists. Documentation completion is intermediate and must not displace remaining product, security, reliability, operability, or merge work.

After every completed action or defer decision, the product loop selects and executes the next highest-value safe item that remains inside the current trust, path, file-count, and patch-size boundaries. Waiting on a check, review, credential, provider, or another writer blocks only the affected item; it does not authorize a status-only finish when disjoint work remains.

Before normal termination, the loop performs two fresh whole-repository exit sweeps over open work, current source, documentation, tests, buyer-visible gaps, and safe path-disjoint candidates. If either sweep finds another safe action, the run continues. The only ordinary no-change finish is evidence that all remaining candidates are externally blocked, already owned, unsafe, outside the declared scope, or infeasible under current authority.

## User-redirection incident

When the user explicitly reports that a prior invocation stopped early or that safe work remained, classify the event as a **user-redirection incident** in the scheduler control plane. Reconstruct the last voluntary termination condition and identify the safe lane that was left executable instead of converting the redirection into another status recap.

A **prompt update earns zero completion credit**. In the **same invocation**, first repair the control-plane wording when the failure class is not already covered, then **rebuild the whole Clearfolio executable queue** from fresh GitHub evidence and perform at least one **substantive repository action** when a safe action exists. A substantive action is a source or documentation mutation backed by acceptance evidence, an exact verification action, a valid duplicate closure, a review-thread remediation, or a protected merge; editing the scheduler prompt alone is not substantive repository progress.

If more than one independent safe lane is executable, **rotate beyond the first safe lane** before considering termination. A wait, approval gate, queued check, documentation completion, prompt repair, or newly discovered blocker remains local to its lane. Writer-lease, exact-head/base, branch protection, review independence, and security gates remain unchanged by the redirection and may never be bypassed to manufacture visible progress.

## Detailed authority and prompt size

The repository-local scheduler is a **thin control plane** for execution. **Repository documents are the detailed authority** for product requirements, architecture, security, privacy, testing, operability, fidelity, release acceptance, and implementation maturity. The recurring model prompt must inspect those reviewed sources before selecting product work; it must not become a second independently maintained PRD/TRD/architecture corpus.

This is a source-of-truth rule, not permission to delete requirements. The prompt retains execution semantics that must be present at invocation time—RCA, feasibility, path ownership, writer safety, TDD, verification, work-conserving continuation, and exit sweeps—while detailed product rules belong in the canonical GitHub graph. If an applicable canonical document exists only on an active PR, the loop may use it only as `ACTIVE_PR` evidence and must never describe it as protected-main shipped behavior.

When a scheduler failure occurs, distinguish scheduling/activation from execution failure using available evidence, simplify duplicated prompt state when it is a plausible failure amplifier, and continue safe repository work in the same invocation. Missing scheduler telemetry is not evidence for an invented hidden root cause.

## Execution receipts and resumable continuation

Issue #331 and ADR-0012 define the cross-control-plane semantics. The **external scheduler** that has emitted a generic task error and repository-local **Draft #271** are distinct authorities. A receipt from one never proves that the other executed, and neither bypasses GitHub review or branch protection.

A **generic scheduled-task error is not completion**. It is an observed control-plane symptom whose internal cause remains unknown unless the executing platform exposes a failing phase or controlled category. The maintainer must not convert missing telemetry into an invented prompt-size, provider, connector, permission, or runtime root cause.

Where the executing platform permits, each run records a privacy-safe semantic set equivalent to:

- `automation_checkpoint`: last safe phase and exact historical repository evidence identity;
- `action_receipt`: the last completed atomic action and its observable proof;
- `failure_envelope`: an observed phase and controlled low-cardinality category, or `telemetry_unavailable`;
- `continuation_handoff`: branch-local deferred work and the next permitted class of action, never write authority.

The recurring OpenCode prompt requires a bounded privacy-safe receipt in its result. It identifies the last safe checkpoint, any completed action receipt, an observed failure envelope when applicable, and whether the run ended as a clean `budget_continuation`. It excludes credentials, authorization values, cookies, approval/artifact tokens, document content, filenames/paths, tenant/subject identifiers, uncontrolled exception text, and stack traces.

Use `budget_continuation` only when actual run/tool signals show that another multi-step mutation cannot reach a verified or safely test-only boundary. It is not repository completion and is not available merely because one branch waits. Before continuing later, leave no temporary/self-modifying/competing writer workflow and no knowingly broken non-test-only branch head.

Every `automation_checkpoint`, `action_receipt`, `failure_envelope`, and `continuation_handoff` is historical after the run. The next invocation must rebuild a fresh queue from live GitHub state and refetch exact source head, live base, target blob/ref, reviews, unresolved threads, checks, security evidence, and writer ownership immediately before another write.

This Draft adds semantic prompt and operator contracts only. The complete durable execution-receipt implementation, phase storage, privacy validation, and platform integration remain `PLANNED` under issue #331 until exact acceptance evidence exists.

## Backpressure and idempotency

The product scheduler counts every page of open pull requests during proposal, verification, and publication, but review or Check latency is not a repository-wide development stop. Open PRs retain exclusive ownership of every path they change. The scheduler may work only on a disjoint path set and may keep no more than two same-repository `automation/hourly-` Draft PRs open at once. The central PR-maintenance plane continues to review, repair, revalidate, and merge the existing queue independently.

The workflow rejects publication when:

- the bounded autonomous Draft queue is full;
- any proposed path overlaps an open pull request path;
- protected `main` moves;
- the patch digest differs;
- a proposal contains a prohibited change class;
- the verifier no longer reproduces the proposal;
- required credentials are absent.

Unique branch names include workflow run and attempt identifiers. Non-cancelling single-flight concurrency prevents product runs from overlapping. Evidence expires after three days. A failed or discarded run leaves no branch, PR, release, deployment, or partial repository mutation. Operators fix the underlying prerequisite and use a later schedule or manual dispatch; they do not bypass the failed gate.

## Standalone and MSA behavior

Clearfolio remains independently operable: the product workflow targets this repository and produces an ordinary Clearfolio Draft. It also remains compatible with the wider ContextualWisdomLab control plane because PR maintenance, review identities, and merge policy are consumed from the organization `.github` service rather than copied locally.

No naruon runtime dependency is introduced. A future naruon module may observe Clearfolio's public API and released artifacts, but autonomous repository maintenance remains an organization control-plane concern and product proposal generation remains a Clearfolio concern.

## Operator verification

1. Confirm `ContextualWisdomLab/.github` has exactly one enabled Clearfolio PR-maintenance caller on its protected default branch.
2. Confirm Clearfolio has no repository-local `hourly-pr-maintenance.yml` duplicate.
3. Confirm the central caller targets `ContextualWisdomLab/clearfolio`, preserves reviewer identities, throttles same-head retries, and cannot approve or bypass protection.
4. Run the product workflow with `dry_run=true`; it should report bounded autonomous-queue capacity and the current open-PR count without invoking OpenCode.
5. Confirm all three product prerequisites are configured without displaying their values.
6. Inspect the proposal job for the pinned OpenCode version and archive checksum, blocked egress, explicit permission map, absence of automatic permission granting, and credential-disclosure scan.
7. Confirm newly created allowed text files are included and that binary, deletion, rename, symlink, mode, workflow, script, dependency, and build-metadata changes are rejected.
8. Confirm the verifier has no model or App credential, uses blocked egress with only reviewed GitHub, Maven Central, and PyPI destinations, and runs both authoritative acceptance command families.
9. Confirm the publisher uses GitHub-only blocked egress, requests only `contents: write` and `pull-requests: write`, and performs only branch push and Draft-PR creation.
10. Confirm the Draft receives normal exact-head review and a qualifying independent approval before any merge.
11. Confirm the product-maintainer prompt treats every completed slice, documentation change, review request, green check, or blocker as intermediate while safe work remains and requires two fresh exit sweeps before a no-work finish.
12. Confirm the prompt reads repository-controlled product/architecture/security/operations authority rather than carrying an independently maintained copy of detailed product requirements.
13. If the user reports premature termination, confirm the incident is classified as a user-redirection incident, the whole queue is rebuilt, prompt repair receives zero completion credit, and substantive repository execution resumes in the same invocation.
14. Confirm the bounded OpenCode result contains no secret-bearing receipt fields and that its checkpoint/action/failure/continuation terms remain historical rather than write authority.

## Failure and rollback

If the central PR-maintenance caller fails, leave existing PRs unchanged, retain exact-head evidence, repair the central workflow in `.github`, and rerun it. Do not restore a repository-local duplicate as a shortcut.

If product proposal generation fails, delete no evidence or protected branch state. Correct the model credential, App installation, egress allowlist, immutable tool pin, or source defect and invoke a later run.

If publication occurs but a later Check fails, the Draft remains open for normal review and repair. The product workflow cannot mark the Check successful, approve the PR, or merge it.

Rollback of this local feature consists of disabling or removing only `hourly-product-development.yml`; PR maintenance continues from the independently versioned central control plane. Receipt rollback returns to fail-closed `telemetry_unavailable` plus fresh-state reconstruction; it must not restore invented causes or stale-checkpoint writes.

## References

Anomaly Innovations. (2026). *OpenCode permission evaluation* [Source code]. GitHub. Retrieved August 5, 2026, from https://github.com/anomalyco/opencode/blob/dev/packages/core/src/permission.ts

Anomaly Innovations. (2026). *OpenCode run command* [Source code]. GitHub. Retrieved August 5, 2026, from https://github.com/anomalyco/opencode/blob/dev/packages/opencode/src/cli/cmd/run.ts

GitHub. (2026). *Automatic token authentication*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub. (2026). *Communication requirements for GitHub-hosted runners*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/actions/reference/runners/github-hosted-runners#communication-requirements-for-github-hosted-runners

GitHub. (2026). *Creating a GitHub App*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/apps/creating-github-apps/registering-github-apps/creating-a-github-app

GitHub. (2026). *Security hardening for GitHub Actions*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

National Institute of Standards and Technology. (2022). *Secure software development framework (SSDF) version 1.1: Recommendations for mitigating the risk of software vulnerabilities* (NIST SP 800-218). U.S. Department of Commerce. https://doi.org/10.6028/NIST.SP.800-218

OpenSSF. (2023). *SLSA threat model*. Supply-chain Levels for Software Artifacts. https://slsa.dev/spec/v1.0/threats-overview

StepSecurity. (2026). *Harden-Runner outbound traffic policy*. StepSecurity Docs. Retrieved August 5, 2026, from https://docs.stepsecurity.io/harden-runner/how-tos/egress-policy
