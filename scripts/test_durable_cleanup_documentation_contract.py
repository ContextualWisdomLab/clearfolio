"""Regression tests for truthful durable artifact-cleanup documentation.

The integrated reference runtime persists deletion intent before metadata
removal, retries incomplete cleanup after restart and on a bounded schedule,
and exposes aggregate evidence. Public operating documents must describe that
implemented boundary while separately preserving the remaining distributed
transaction and remote object-store limitations.
"""

from pathlib import Path
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
AGENT_GUIDE = REPOSITORY_ROOT / "AGENTS.md"
AUTHORIZATION_DECISION = (
    REPOSITORY_ROOT
    / "docs"
    / "security"
    / "2026-08-05-administrative-authorization.md"
)
DEPLOYMENT_PLAYBOOK = (
    REPOSITORY_ROOT
    / "docs"
    / "deployment"
    / "2026-07-02-buyer-deployment-integration-playbook.md"
)


class DurableCleanupDocumentationContractTest(unittest.TestCase):
    """Keeps buyer and operator claims aligned with the integrated runtime."""

    def test_agent_guide_describes_integrated_worker_and_remaining_limits(self) -> None:
        """The agent source of truth must not describe cleanup as absent."""
        content = AGENT_GUIDE.read_text(encoding="utf-8")

        self.assertIn("receipt-first cleanup worker", content)
        self.assertIn("cross-resource transactional outbox", content)
        self.assertNotIn(
            "does not implement a failed-artifact\n  cleanup queue, deletion receipt, outbox, or retry worker",
            content,
        )

    def test_authorization_decision_distinguishes_local_durability_from_distributed_atomicity(
        self,
    ) -> None:
        """The security decision must state both implemented and residual risk."""
        content = AUTHORIZATION_DECISION.read_text(encoding="utf-8")

        self.assertIn("durable deletion receipt", content)
        self.assertIn("bounded scheduled recovery", content)
        self.assertIn("distributed generation fence", content)
        self.assertNotIn("This slice does not persist a deletion receipt", content)
        self.assertNotIn("No durable artifact-cleanup evidence exists in this slice", content)

    def test_deployment_playbook_exposes_recovery_configuration_and_cutover_limits(
        self,
    ) -> None:
        """Buyer operations must configure the worker without overstating it."""
        content = DEPLOYMENT_PLAYBOOK.read_text(encoding="utf-8")

        self.assertIn("artifact-deletion-receipts.log", content)
        self.assertIn("bounded scheduled recovery", content)
        self.assertIn("remote-object-store atomicity", content)
        self.assertNotIn("does not configure `ArtifactCleanupQueue`", content)
        self.assertNotIn("A removal failure is currently best effort", content)


if __name__ == "__main__":
    unittest.main()
