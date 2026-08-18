#!/usr/bin/env python3
"""Require acquisition diligence to use current canonical evidence, not stale snapshots."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def read_text(relative_path: str) -> str:
    """Return one repository text file using strict UTF-8 decoding."""

    return (REPOSITORY_ROOT / relative_path).read_text(encoding="utf-8")


class AcquisitionDiligenceContractTest(unittest.TestCase):
    """Keep acquisition readiness reconstructable from current repository authority."""

    def test_current_acquisition_diligence_document_exists_and_is_discoverable(self) -> None:
        """Require one non-dated canonical diligence authority linked from architecture."""

        diligence_path = REPOSITORY_ROOT / "docs/ACQUISITION_DILIGENCE.md"
        self.assertTrue(diligence_path.is_file())
        architecture = read_text("ARCHITECTURE.md")
        assessment = read_text("docs/DOCUMENTATION_ASSESSMENT.md")
        self.assertIn("`docs/ACQUISITION_DILIGENCE.md`", architecture)
        self.assertIn("`docs/ACQUISITION_DILIGENCE.md`", assessment)

    def test_diligence_separates_current_evidence_from_legacy_snapshots(self) -> None:
        """Prevent dated sale-readiness material from masquerading as current proof."""

        diligence = read_text("docs/ACQUISITION_DILIGENCE.md").lower()
        for required in (
            "protected main",
            "active_pr",
            "historical evidence",
            "license",
            "third-party attribution",
            "sbom",
            "intellectual property",
            "contributor",
            "legal",
            "issue #5",
            "issue #263",
            "production office",
            "durable asynchronous",
            "opentelemetry",
            "independent approval",
        ):
            with self.subTest(required=required):
                self.assertIn(required, diligence)

        self.assertIn("2026-07-02-buyer-diligence-index.md", diligence)
        self.assertIn("must not be used to assert", diligence)

        current_work = diligence.split(
            "## current open work that materially affects diligence",
            maxsplit=1,
        )[1].split("## historical evidence policy", maxsplit=1)[0]
        self.assertNotIn("#74", current_work)
        self.assertNotIn("#82", current_work)

    def test_diligence_does_not_claim_unproven_transfer_or_certification(self) -> None:
        """Require rights/compliance gaps to remain explicit until independently proven."""

        diligence = read_text("docs/ACQUISITION_DILIGENCE.md").lower()
        self.assertIn("not independently proven", diligence)
        self.assertIn("does not claim certification", diligence)
        self.assertIn("external legal review", diligence)


if __name__ == "__main__":
    unittest.main()
