#!/usr/bin/env python3
"""Regression tests for buyer-facing operational documentation contracts."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
THREAT_MODEL_PATH = (
    REPOSITORY_ROOT / "docs" / "security" / "2026-07-02-threat-model-data-handling.md"
)


class DocumentationContractsTest(unittest.TestCase):
    """Protect terminology that affects deployment and security decisions."""

    def test_threat_model_distinguishes_liveness_from_readiness(self) -> None:
        """Require the threat model to name both probes with their shipped roles."""
        threat_model_lines = THREAT_MODEL_PATH.read_text(encoding="utf-8").splitlines()
        health_line = next(
            line for line in threat_model_lines if line.startswith("- `GET /healthz`:")
        )
        readiness_line = next(
            line for line in threat_model_lines if line.startswith("- `GET /readyz`:")
        )

        self.assertRegex(health_line, r": .*liveness probe")
        self.assertNotIn("readiness", health_line.lower())
        self.assertRegex(readiness_line, r": .*readiness probe")
        self.assertNotIn("liveness", readiness_line.lower())


if __name__ == "__main__":
    unittest.main()
