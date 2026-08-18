#!/usr/bin/env python3
"""Prevent the documentation fitness matrix from regressing security authority."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    """Return one repository text file with strict UTF-8 decoding."""

    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


class DocumentationAssessmentSecurityContractTest(unittest.TestCase):
    """Keep the documentation assessment aligned with the canonical security entrypoint."""

    def test_security_fitness_row_matches_current_root_security_authority(self) -> None:
        """Reject the superseded reporting-policy-only description of SECURITY.md."""

        security = " ".join(read_text("SECURITY.md").lower().split())
        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")

        for required in (
            "signed artifact",
            "same-tenant",
            "hmac",
            "document conversion trust boundary",
            "release evidence",
        ):
            with self.subTest(required=required):
                self.assertIn(required, security)

        self.assertNotIn("root `SECURITY.md` remains the reporting policy", assessment)
        self.assertIn(
            "root `SECURITY.md` is the current product security entrypoint",
            assessment,
        )


if __name__ == "__main__":
    unittest.main()
