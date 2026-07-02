#!/usr/bin/env python3
"""Unit tests for the buyer data-room manifest checker."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_buyer_dataroom_manifest import check_manifest


class BuyerDataRoomManifestTest(unittest.TestCase):

    def test_accepts_existing_artifacts_urls_and_gate_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "docs" / "evidence.md").write_text("ok\n", encoding="utf-8")

            result = check_manifest(root, {
                "manifestVersion": 1,
                "packageName": "Clearfolio Buyer Data Room",
                "artifacts": [
                    {"id": "evidence", "path": "docs/evidence.md", "status": "ready"},
                    {"id": "figjam", "url": "https://www.figma.com/board/example", "status": "ready"},
                ],
                "readinessGates": [
                    {"id": "design", "status": "ready", "evidence": ["evidence", "figjam"]}
                ],
            })

            self.assertEqual([], result["errors"])
            self.assertEqual(2, result["artifactCount"])
            self.assertEqual(1, result["gateCount"])

    def test_reports_missing_artifact_path(self) -> None:
        result = check_manifest(Path.cwd(), {
            "manifestVersion": 1,
            "packageName": "Clearfolio Buyer Data Room",
            "artifacts": [
                {"id": "missing", "path": "docs/missing.md", "status": "ready"}
            ],
            "readinessGates": [],
        })

        self.assertIn("artifact missing path does not exist: docs/missing.md", result["errors"])

    def test_reports_gate_without_evidence(self) -> None:
        result = check_manifest(Path.cwd(), {
            "manifestVersion": 1,
            "packageName": "Clearfolio Buyer Data Room",
            "artifacts": [],
            "readinessGates": [
                {"id": "empty-gate", "status": "ready", "evidence": []}
            ],
        })

        self.assertIn("gate empty-gate must reference evidence", result["errors"])

    def test_reports_unknown_gate_evidence_reference(self) -> None:
        result = check_manifest(Path.cwd(), {
            "manifestVersion": 1,
            "packageName": "Clearfolio Buyer Data Room",
            "artifacts": [],
            "readinessGates": [
                {"id": "unknown-gate", "status": "partial", "evidence": ["missing-artifact"]}
            ],
        })

        self.assertIn(
            "gate unknown-gate references unknown artifact: missing-artifact",
            result["errors"],
        )


if __name__ == "__main__":
    unittest.main()
