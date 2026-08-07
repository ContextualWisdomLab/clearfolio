# Post-token publication gate for autonomous Draft PRs

Date: 2026-08-07  
Status: Accepted

## Context

The hourly product-development workflow separates model execution, credential-free verification, and publication. The publication job originally verified the paginated open-PR inventory, protected-base SHA, and immutable patch digest before minting the short-lived Maintainer GitHub App token.

That ordering left a bounded time-of-check/time-of-use interval after token minting. Another pull request could open, protected `main` could move, or the proposal artifact could change before the first branch write. The workflow would still create a stale or duplicate Draft. It could not merge or bypass protection, but it could violate the one-product-proposal backpressure contract and add avoidable reviewer and CI load.

GitHub installation access tokens are short lived and can be restricted to selected repositories and a subset of the App's installed permissions. They still carry the requested write authority until expiration, so authorization should occur as late as practical and every mutable precondition must be revalidated immediately before its first use.

## Decision

After the repository-scoped App token is minted and before any `git apply`, branch creation, commit, push, or pull-request creation, the publisher performs a final fail-closed gate using the installation token.

The final gate:

1. queries every page of open pull requests and requires a count of zero;
2. compares the checked-out protected-base SHA with the independently verified expected SHA;
3. recomputes and compares the immutable proposal patch SHA-256;
4. initializes `publish=false` before evaluation;
5. returns without repository mutation when any precondition differs;
6. emits `publish=true` only when every value still matches;
7. guards the sole write step with the final gate output.

No repository write occurs between token minting and this recheck. The token retains only `contents: write` and `pull-requests: write` for `ContextualWisdomLab/clearfolio`. The workflow contains no approval, merge, auto-merge, release, package publication, deployment, ruleset, administration, secret, workflow, or security-event permission or command.

## Security and reliability consequences

The change narrows the publication TOCTOU window to the unavoidable interval between the final API/SHA checks and the immediately following write step. It cannot make the GitHub API query and branch push transactional, so protected-branch rules, unique branch names, Draft-only creation, ordinary exact-head checks, independent approval, and central PR maintenance remain mandatory compensating controls.

A competing pull request or base update after the final gate can still occur. The result remains an unmerged Draft on a unique branch, not a protected-branch mutation. Central PR maintenance then treats that Draft through normal exact-head governance. The workflow never deletes a competing PR, overwrites an existing branch, approves itself, or infers merge readiness.

The check is repeated rather than cached because GitHub's open-PR inventory and protected branch are mutable external state. Patch identity is repeated because the artifact crosses jobs and the write-capable job is the final trust boundary.

## Verification

`scripts/test_hourly_opencode_scheduler_contract.py` requires:

- at least four complete paginated open-PR queries across proposal, verification, pre-token publication, and post-token publication;
- App-token minting before the final gate and the final gate before Draft creation;
- exact protected-base and patch identity checks in the final gate;
- a fail-closed default output;
- the Draft creation step to depend on `steps.final_publish_gate.outputs.publish == 'true'`;
- absence of merge, auto-merge, release, and deployment commands.

The canonical repository checks remain `mvn -B --no-transfer-progress verify` and `python -m pytest -q scripts` on the exact current head and synthetic merge result. A predecessor-head pass does not validate this workflow.

## Rollback

Rollback removes the final post-token recheck and restores the Draft step's pre-token gate dependency. That rollback reopens the duplicate/stale-publication interval and therefore requires an explicit security review. Disabling the product scheduler is safer than weakening this gate.

## References

GitHub. (2026). *Authenticating as a GitHub App installation*. GitHub Docs. Retrieved August 7, 2026, from https://docs.github.com/en/enterprise-cloud@latest/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app-installation

GitHub. (2026). *Generating an installation access token for a GitHub App*. GitHub Docs. Retrieved August 7, 2026, from https://docs.github.com/en/enterprise-cloud@latest/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app

National Institute of Standards and Technology. (2024). *Secure software development practices for generative AI and dual-use foundation models: An SSDF community profile* (NIST SP 800-218A). U.S. Department of Commerce. https://doi.org/10.6028/NIST.SP.800-218A

Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure software development framework (SSDF) version 1.1: Recommendations for mitigating the risk of software vulnerabilities* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218
