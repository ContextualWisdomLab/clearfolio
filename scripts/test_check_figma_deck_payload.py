#!/usr/bin/env python3
"""Unit tests for the Figma deck payload checker."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_figma_deck_payload import check_payload


class FigmaDeckPayloadTest(unittest.TestCase):

    def test_accepts_buyer_diligence_payload_with_claim_boundary(self) -> None:
        result = check_payload({
            "title": "Clearfolio Buyer Demo Diligence Pack",
            "description": "Generate a buyer diligence deck. Figma Code Connect must not be used.",
            "objectives": [
                "Show buyer-demo evidence.",
                "Keep partial gates visible.",
                "State that KRW 2B is not a valuation opinion.",
            ],
            "outline": [
                {
                    "role": "cover_slide",
                    "subject": "Clearfolio Buyer Demo Diligence Pack",
                    "purpose": "Set context.",
                    "visuals": ["Title"],
                    "content": ["KRW 2B sale-readiness evidence package"],
                },
                {
                    "role": "metrics",
                    "subject": "Buyer Readiness Scorecard",
                    "purpose": "Quantify evidence readiness.",
                    "visuals": ["Readiness rollup"],
                    "content": ["38 percent conservative gate readiness"],
                },
                {
                    "role": "closing",
                    "subject": "Claim Boundary",
                    "purpose": "Prevent overclaiming.",
                    "visuals": ["Boundary statement"],
                    "content": ["KRW 2B is a sale-readiness target, not a valuation opinion"],
                },
            ],
        })

        self.assertEqual([], result["errors"])
        self.assertEqual(3, result["slideCount"])

    def test_requires_no_code_connect_claim_boundary_and_minimum_outline(self) -> None:
        result = check_payload({
            "title": "Clearfolio Buyer Demo Diligence Pack",
            "description": "Generate slides.",
            "objectives": [],
            "outline": [
                {
                    "role": "cover_slide",
                    "subject": "Cover",
                    "purpose": "Set context.",
                    "visuals": [],
                    "content": ["Hello"],
                }
            ],
        })

        self.assertIn("description must state that Figma Code Connect is not used", result["errors"])
        self.assertIn("at least 3 objectives are required", result["errors"])
        self.assertIn("at least 3 outline slides are required", result["errors"])
        self.assertIn("outline must include a claim boundary closing slide", result["errors"])


if __name__ == "__main__":
    unittest.main()
