# Hourly autonomous development and pull-request maintenance

## Purpose

Clearfolio uses two independent hourly workflows so review latency never stops productive work while product-development automation never competes with an open pull request.

- `.github/workflows/hourly-pr-maintenance.yml` runs at minute 7. It inventories every open pull request, dispatches the existing organization review/fix loop, rechecks exact-head review and Check evidence, updates eligible branches, and permits merge or guarded auto-merge only through the centrally governed workflow.
- `.github/workflows/hourly-product-development.yml` runs at minute 23. It proposes exactly one bounded buyer-visible increment only when the paginated open-pull-request count is zero and protected `main` remains unchanged throughout proposal, verification, and publication.

The schedules are offset to avoid unnecessary runner contention. Both workflows use non-cancelling concurrency groups, so a slower prior run cannot be silently replaced by a newer run.

## Review-agent credential boundary

The PR-maintenance workflow calls these immutable central sources at commit `74e54255ec903e3ba5f920859b656fe2defcb057`:

- `pr-review-fix-scheduler.yml`;
- `pr-review-merge-scheduler.yml`.

Both calls use `secrets: inherit`. Clearfolio does not rename, remap, replace, or expose the centrally managed reviewer credentials. The workflow does not reference `NVIDIA_NIM_API_KEY`; its purpose is to preserve the existing review-agent identity and repository-protection model.

The central merge scheduler remains responsible for evaluating current-head review evidence, required Checks, branch updates, and merge eligibility. The local hourly caller does not approve its own work or bypass independent approval, security gates, expected-head checks, or branch protection.

## Product-maintainer identity and prerequisites

The product scheduler uses checksum-pinned OpenCode 1.18.13. The reviewed Linux x64 archive SHA-256 is `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937`.

Repository administrators must configure these values before a non-dry run can produce a proposal:

- organization or repository secret `NVIDIA_NIM_API_KEY`;
- repository variable `CLEARFOLIO_MAINTAINER_APP_CLIENT_ID`;
- repository secret `CLEARFOLIO_MAINTAINER_APP_PRIVATE_KEY`.

The workflow maps `NVIDIA_NIM_API_KEY` into the process-local `NVIDIA_API_KEY` variable expected by the pinned OpenCode NVIDIA provider. The value is masked, never written to source, and scanned against model output and every model-writable path. Missing credentials fail closed by skipping autonomous development with a notice. They never cause fallback to another provider, an unauthenticated model, GitHub Copilot, or a broader workflow token.

The Maintainer App must be installed only for `ContextualWisdomLab/clearfolio` and should receive the minimum repository permissions needed to create a branch and draft pull request. The token-minting action further downscopes every publication token to the two GitHub permission categories required by those operations: `contents: write` and `pull-requests: write`. GitHub groups branch pushes and release APIs under `contents`, and draft-PR creation and merge APIs under `pull-requests`; it does not offer narrower branch-only or draft-only token permissions. Clearfolio therefore combines the narrowest available token categories with immutable patch identity, a publication-only job, explicit draft-only commands, regression tests prohibiting merge/release behavior, and branch protection. The App must not receive administration, ruleset, secret, environment, security-event, deployment, workflow, or branch-protection bypass authority.

## Three-stage trust separation

### 1. Credentialed proposal

The model runner has repository read access and a narrowly enumerated edit boundary: `src/main/**`, `src/test/**`, `docs/**`, `README.md`, and `CHANGELOG.md`. `.github/**`, `scripts/**`, `pom.xml`, lockfiles, environment files, `.git/**`, external directories, web search, web fetch, nested tasks, interactive questions, skills, and unapproved shell commands are denied. Protecting `pom.xml` prevents model output from changing dependencies, Maven plugins, versions, or other executable build inputs before the credential-free verifier runs Maven.

OpenCode is deliberately invoked without `--auto`. In its non-interactive `run` command, an unmatched permission request is rejected rather than automatically approved. Explicitly allowed read, edit, search, and inspection operations still run, while a new tool, an unexpected resource pattern, or any other permission that falls back to `ask` fails closed.

The credentialed step does not execute model-modified repository code. In particular, it cannot run Maven, pytest, project modules, code generation, language servers, commits, pushes, or GitHub CLI publication. It may inspect only `git status --short`, `git diff --stat`, and `git diff --check`. This prevents untrusted proposed code from reading the NVIDIA credential or runner tokens.

The proposal must be test-first, one bounded vertical slice, no more than 20 changed files and 200,000 patch bytes, without deletion, rename, symlink, binary, mode, workflow, script, lockfile, dependency, build-metadata, version, release, deployment, approval, or merge changes. Newly created files inside the allowed text-source, test, and documentation boundary are first represented with Git intent-to-add so they are included in the same immutable patch and counted against the file budget. Git `numstat` then rejects any tracked or newly created binary payload before evidence is packaged. The workflow packages an immutable Git patch, base SHA, patch SHA-256, stat summary, and OpenCode result for three days.

### 2. Credential-free verifier

A fresh runner checks out protected `main`, downloads the immutable proposal, and discards it when any pull request exists, `main` moved, or the patch hash differs. It applies the patch with `git apply --check`, runs `git diff --check`, then executes:

```bash
mvn -B --no-transfer-progress verify
python -m pip install --disable-pip-version-check --no-cache-dir --require-hashes -r requirements-test.txt
python -m pytest -q scripts
```

This job receives neither the NVIDIA key nor the Maintainer App private key. It is therefore the credential-free verifier for production tests, zero-missed line and branch coverage, warning-free public Javadocs, packaging, deterministic buyer evidence, and workflow contract tests.

Credential-free does not mean trusted-code-only: the verifier executes the proposed test and production patch. Its Harden Runner policy therefore uses fail-closed `egress-policy: block`, not observation-only audit mode. The allowlist is limited to GitHub API, checkout and artifact transport endpoints, Maven Central, and the PyPI hosts needed for the hash-locked `requirements-test.txt` installation. A generated test cannot contact an arbitrary external destination, while every permitted package remains constrained by immutable action pins or committed dependency hashes. Any newly required endpoint must be reviewed as a security-sensitive workflow-source change and added narrowly rather than switching the verifier back to audit mode.

### 3. Publication-only identity

A third fresh runner rechecks the complete paginated PR inventory, protected base SHA, and patch SHA-256. Only then does it mint a short-lived repository-scoped Maintainer App token limited to the narrowest available GitHub categories, `contents: write` and `pull-requests: write`, apply the already verified patch, push a unique automation branch, and open a draft pull request.

Because the publisher handles the App private key and a short-lived write token, its Harden Runner policy also uses fail-closed `egress-policy: block`. Its allowlist contains only the GitHub API, checkout, release-asset, and Actions artifact-transport endpoints required to download the verified proposal, mint the scoped token, push the branch, and create the draft pull request. NVIDIA, Maven Central, PyPI, and arbitrary external destinations are deliberately absent. A compromised action or publication command therefore cannot export the App credential to an unrelated host.

The publication workflow source does not execute proposed code or invoke approval, auto-merge, merge, release, package publication, or deployment operations. The resulting draft enters the same exact-head CI, Security Scan, SAST, fuzzing, CodeRabbit, OpenCode/Noema/Strix, unresolved-thread, independent-approval, repository-policy, and branch-protection loop as a human-authored change.

## Backpressure, idempotency, and failure recovery

The product scheduler counts every page of open pull requests at proposal, verification, and publication. A single open PR transfers ownership to the PR-maintenance loop and prevents another autonomous product branch. A protected-base movement or patch-identity mismatch discards the proposal rather than rebasing unreviewed output.

Unique branch names include the workflow run and attempt identifiers. Non-cancelling concurrency prevents two product runs from overlapping. Artifacts expire after three days and contain no credential. A failed or discarded run leaves no branch, pull request, release, deployment, or partial repository mutation. Operators correct the prerequisite or underlying gate and use the next scheduled run or `workflow_dispatch`; they do not bypass the gate.

## Operator verification

1. Confirm both workflows are enabled in the Actions UI and their scheduled events are not disabled by repository inactivity.
2. Run the product workflow with `dry_run=true`. It should report either `open_pull_request` ownership or readiness without invoking OpenCode.
3. Confirm the three product prerequisites are configured without displaying their values.
4. Inspect the first credentialed run for the pinned OpenCode version and archive checksum, blocked egress policy, explicit permission map, absence of `--auto`, and credential-disclosure scan.
5. Confirm the proposal boundary includes allowed newly created text files, counts them against the 20-file limit, and rejects binary, deletion, rename, symlink, mode, workflow, script, dependency, and build-metadata changes before upload.
6. Confirm the verifier has no model or App credential, uses `egress-policy: block` with only reviewed GitHub, Maven Central, and PyPI endpoints, and runs both authoritative acceptance commands.
7. Confirm the publisher uses `egress-policy: block` with GitHub-only endpoints, requests only `contents: write` and `pull-requests: write`, invokes only branch push and draft-PR creation, and leaves normal exact-head Checks and independent approval mandatory.
8. Inspect every scheduler-source or endpoint-allowlist update as a security-sensitive workflow change; never replace immutable commit pins with tags or branches and never broaden verifier or publisher egress to audit mode.

## References

Anomaly Innovations. (2026). *OpenCode permission evaluation* [Source code]. GitHub. Retrieved August 5, 2026, from https://github.com/anomalyco/opencode/blob/dev/packages/core/src/permission.ts

Anomaly Innovations. (2026). *OpenCode run command* [Source code]. GitHub. Retrieved August 5, 2026, from https://github.com/anomalyco/opencode/blob/dev/packages/opencode/src/cli/cmd/run.ts

GitHub. (2026). *Automatic token authentication*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub. (2026). *Communication requirements for GitHub-hosted runners*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/actions/reference/runners/github-hosted-runners#communication-requirements-for-github-hosted-runners

GitHub. (2026). *Creating a GitHub App*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/apps/creating-github-apps/registering-a-github-app/creating-a-github-app

GitHub. (2026). *Security hardening for GitHub Actions*. GitHub Docs. Retrieved August 5, 2026, from https://docs.github.com/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

National Institute of Standards and Technology. (2022). *Secure software development framework (SSDF) version 1.1: Recommendations for mitigating the risk of software vulnerabilities* (NIST SP 800-218). U.S. Department of Commerce. https://doi.org/10.6028/NIST.SP.800-218

OpenSSF. (2023). *SLSA threat model*. Supply-chain Levels for Software Artifacts. https://slsa.dev/spec/v1.0/threats-overview

StepSecurity. (2026). *Harden-Runner outbound traffic policy*. StepSecurity Docs. Retrieved August 5, 2026, from https://docs.stepsecurity.io/harden-runner/how-tos/egress-policy
