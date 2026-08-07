"""Verify that stacked pull requests retain the complete CI acceptance contract."""

from pathlib import Path
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
CI_WORKFLOW = REPOSITORY_ROOT / ".github" / "workflows" / "ci.yml"


class CiWorkflowStackCoverageTest(unittest.TestCase):
    """Exercises the stacked-pull-request workflow contract with standard discovery."""

    def test_ci_runs_for_every_pull_request_base(self) -> None:
        """Stacked pull requests must receive the same exact-head CI as main-bound PRs."""
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("  pull_request: {}", workflow)
        self.assertNotIn("  pull_request:\n    branches: [main]", workflow)

    def test_ci_preserves_exact_head_and_synthetic_merge_evidence(self) -> None:
        """Broadening PR coverage must not weaken exact-head or merge verification."""
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")

        exact_head_expression = "github.event.pull_request.head.sha || github.sha"
        self.assertGreaterEqual(workflow.count(exact_head_expression), 4)
        self.assertIn('test "$(git rev-parse HEAD)" = "$EXPECTED_SHA"', workflow)
        self.assertIn("name: Maven merge compatibility", workflow)
        self.assertIn("name: Buyer-readiness script tests", workflow)


if __name__ == "__main__":
    unittest.main()
