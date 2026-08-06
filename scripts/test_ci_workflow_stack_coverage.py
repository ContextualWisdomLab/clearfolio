from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
CI_WORKFLOW = REPOSITORY_ROOT / ".github" / "workflows" / "ci.yml"


def test_ci_runs_for_every_pull_request_base() -> None:
    """Stacked pull requests must receive the same exact-head CI as main-bound PRs."""
    workflow = CI_WORKFLOW.read_text(encoding="utf-8")

    assert "  pull_request: {}" in workflow
    assert "  pull_request:\n    branches: [main]" not in workflow


def test_ci_preserves_exact_head_and_synthetic_merge_evidence() -> None:
    """Broadening PR coverage must not weaken exact-head or merge verification."""
    workflow = CI_WORKFLOW.read_text(encoding="utf-8")

    exact_head_expression = "github.event.pull_request.head.sha || github.sha"
    assert workflow.count(exact_head_expression) >= 4
    assert 'test "$(git rev-parse HEAD)" = "$EXPECTED_SHA"' in workflow
    assert "name: Maven merge compatibility" in workflow
    assert "name: Buyer-readiness script tests" in workflow
