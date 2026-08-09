"""Verify that stacked pull requests retain the complete CI acceptance contract."""

from pathlib import Path
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
CI_WORKFLOW = REPOSITORY_ROOT / ".github" / "workflows" / "ci.yml"


def _workflow_jobs(workflow: str) -> dict[str, str]:
    """Return top-level workflow job bodies without adding a YAML dependency."""
    jobs: dict[str, list[str]] = {}
    current_job: str | None = None
    in_jobs = False

    for line in workflow.splitlines():
        if line == "jobs:":
            in_jobs = True
            continue
        if not in_jobs:
            continue
        if line and not line.startswith(" "):
            break
        if line.startswith("  ") and not line.startswith("    ") and line.endswith(":"):
            current_job = line.strip()[:-1]
            jobs[current_job] = []
            continue
        if current_job is not None:
            jobs[current_job].append(line)

    return {job: "\n".join(body) for job, body in jobs.items()}


class CiWorkflowStackCoverageTest(unittest.TestCase):
    """Exercises the stacked-pull-request workflow contract with standard discovery."""

    def test_ci_runs_for_every_pull_request_base(self) -> None:
        """Stacked pull requests must receive the same exact-head CI as main-bound PRs."""
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("  pull_request: {}", workflow)
        self.assertNotIn("  pull_request:\n    branches: [main]", workflow)

    def test_ci_preserves_exact_head_and_synthetic_merge_evidence(self) -> None:
        """Bind exact-head and synthetic-merge checks to their intended CI jobs."""
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        jobs = _workflow_jobs(workflow)
        maven_job = jobs["test"]
        merge_job = jobs["merge-compatibility"]
        script_job = jobs["script-checks"]

        exact_head_expression = "github.event.pull_request.head.sha || github.sha"
        checkout_action = (
            "uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1"
        )
        revision_assertion = 'test "$(git rev-parse HEAD)" = "$EXPECTED_SHA"'

        self.assertIn(checkout_action, maven_job)
        self.assertIn(f"ref: ${{{{ {exact_head_expression} }}}}", maven_job)
        self.assertIn(f"EXPECTED_SHA: ${{{{ {exact_head_expression} }}}}", maven_job)
        self.assertIn(revision_assertion, maven_job)

        self.assertIn(checkout_action, merge_job)
        self.assertNotIn("ref: ${{ github.event.pull_request.head.sha", merge_job)
        self.assertIn("EXPECTED_SHA: ${{ github.sha }}", merge_job)
        self.assertIn(revision_assertion, merge_job)
        self.assertIn("mvn -B --no-transfer-progress verify", merge_job)
        self.assertIn("python3 scripts/verify_maven_test_reports.py", merge_job)

        self.assertIn("name: Buyer-readiness script tests", script_job)

    def test_job_parser_does_not_merge_sibling_job_commands(self) -> None:
        """A command in one sibling job must never satisfy another job's contract."""
        jobs = _workflow_jobs(
            "jobs:\n"
            "  first:\n"
            "    steps:\n"
            "      - run: echo first\n"
            "  second:\n"
            "    steps:\n"
            "      - run: echo second\n"
        )

        self.assertIn("echo first", jobs["first"])
        self.assertNotIn("echo second", jobs["first"])
        self.assertIn("echo second", jobs["second"])
        self.assertNotIn("echo first", jobs["second"])


if __name__ == "__main__":
    unittest.main()
