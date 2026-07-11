#!/usr/bin/env python3
"""Unit tests for buyer-readiness scorecard generation."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from summarize_buyer_readiness import render_markdown, summarize_manifest


class BuyerReadinessScorecardTest(unittest.TestCase):

    def test_summarizes_manifest_status_counts_and_gate_score(self) -> None:
        summary = summarize_manifest({
            "manifestVersion": 1,
            "packageName": "Clearfolio Buyer Data Room",
            "updatedAt": "2026-07-03",
            "artifacts": [
                {"id": "demo", "title": "Buyer demo", "status": "ready"},
                {"id": "oidc", "title": "OIDC profile", "status": "partial"},
                {"id": "legal", "title": "Legal review", "status": "external"},
            ],
            "readinessGates": [
                {"id": "product-demo", "status": "ready", "evidence": ["demo"]},
                {
                    "id": "security-compliance",
                    "status": "partial",
                    "evidence": ["oidc", "legal"],
                },
            ],
        })

        self.assertEqual(3, summary["artifactCount"])
        self.assertEqual({"ready": 1, "partial": 1, "external": 1}, summary["artifactStatusCounts"])
        self.assertEqual(2, summary["gateCount"])
        self.assertEqual({"ready": 1, "partial": 1, "external": 0}, summary["gateStatusCounts"])
        self.assertEqual(50, summary["conservativeGateReadinessPercent"])
        self.assertEqual(["security-compliance"], [gate["id"] for gate in summary["discountRiskGates"]])
        self.assertTrue(summary["readyGateEvidenceIntegrity"])
        self.assertEqual([], summary["readyGateEvidenceViolations"])

    def test_flags_ready_gate_evidence_integrity_violations(self) -> None:
        summary = summarize_manifest({
            "manifestVersion": 1,
            "packageName": "Clearfolio Buyer Data Room",
            "updatedAt": "2026-07-03",
            "artifacts": [
                {"id": "demo-draft", "title": "Buyer demo draft", "status": "partial"}
            ],
            "readinessGates": [
                {"id": "product-demo", "status": "ready", "evidence": ["demo-draft"]}
            ],
        })

        self.assertFalse(summary["readyGateEvidenceIntegrity"])
        self.assertEqual(
            [
                {
                    "gateId": "product-demo",
                    "artifactId": "demo-draft",
                    "artifactStatus": "partial",
                }
            ],
            summary["readyGateEvidenceViolations"],
        )

    def test_markdown_labels_partial_gates_as_discount_risks(self) -> None:
        markdown = render_markdown({
            "packageName": "Clearfolio Buyer Data Room",
            "updatedAt": "2026-07-03",
            "artifactCount": 2,
            "artifactStatusCounts": {"ready": 1, "partial": 1, "external": 0},
            "gateCount": 2,
            "gateStatusCounts": {"ready": 1, "partial": 1, "external": 0},
            "conservativeGateReadinessPercent": 50,
            "readyGateEvidenceIntegrity": False,
            "readyGateEvidenceViolations": [
                {
                    "gateId": "product-demo",
                    "artifactId": "demo-draft",
                    "artifactStatus": "partial",
                }
            ],
            "gateSummaries": [
                {
                    "id": "product-demo",
                    "status": "ready",
                    "evidenceStatuses": ["demo=ready"],
                    "buyerInterpretation": "Ready for buyer walkthrough.",
                },
                {
                    "id": "security-compliance",
                    "status": "partial",
                    "evidenceStatuses": ["oidc=partial"],
                    "buyerInterpretation": "Discount risk until closed.",
                },
            ],
            "discountRiskGates": [
                {"id": "security-compliance", "status": "partial"}
            ],
        })

        self.assertIn("Conservative gate readiness | 50 percent", markdown)
        self.assertIn("Ready gate evidence integrity | Fail: 1 violation(s)", markdown)
        self.assertIn("| security-compliance | partial | oidc=partial | Discount risk until closed. |", markdown)
        self.assertIn("- `security-compliance` remains `partial`.", markdown)
        self.assertIn(
            "- `product-demo` cites `demo-draft=partial` while marked `ready`.",
            markdown,
        )


if __name__ == "__main__":
    unittest.main()
