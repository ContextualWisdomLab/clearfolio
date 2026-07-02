#!/usr/bin/env python3
"""Unit tests for the Clearfolio SBOM license policy checker."""

from __future__ import annotations

import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_sbom_license_policy import evaluate


POLICY = {
    "allowedLicenses": ["Apache-2.0", "MIT"],
    "reviewRequiredComponents": [
        {"purl": "pkg:maven/example/review@1?type=jar", "reason": "legal review"}
    ],
}


def component(name: str, purl: str, licenses: list[str]) -> dict:
    return {
        "group": "example",
        "name": name,
        "version": "1",
        "purl": purl,
        "licenses": [{"license": {"id": value}} for value in licenses],
    }


class LicensePolicyTest(unittest.TestCase):

    def test_allows_permissive_components(self) -> None:
        result = evaluate({
            "bomFormat": "CycloneDX",
            "specVersion": "1.6",
            "components": [
                component("allowed", "pkg:maven/example/allowed@1?type=jar", ["Apache-2.0"])
            ],
        }, POLICY, False)

        self.assertEqual(1, result["allowedCount"])
        self.assertEqual(0, result["reviewRequiredCount"])
        self.assertEqual(0, result["violationCount"])

    def test_tracks_known_review_component_without_violation(self) -> None:
        result = evaluate({
            "components": [
                component("review", "pkg:maven/example/review@1?type=jar", ["LGPL-2.1"])
            ],
        }, POLICY, False)

        self.assertEqual(0, result["allowedCount"])
        self.assertEqual(1, result["reviewRequiredCount"])
        self.assertEqual(0, result["violationCount"])

    def test_require_no_review_fails_known_review_component(self) -> None:
        result = evaluate({
            "components": [
                component("review", "pkg:maven/example/review@1?type=jar", ["LGPL-2.1"])
            ],
        }, POLICY, True)

        self.assertEqual(1, result["reviewRequiredCount"])
        self.assertEqual(1, result["violationCount"])

    def test_flags_missing_license_metadata(self) -> None:
        result = evaluate({
            "components": [
                {"name": "unknown", "version": "1", "purl": "pkg:maven/example/unknown@1"}
            ],
        }, POLICY, False)

        self.assertEqual(1, result["violationCount"])

    def test_flags_unlisted_restrictive_license(self) -> None:
        result = evaluate({
            "components": [
                component("blocked", "pkg:maven/example/blocked@1?type=jar", ["GPL-3.0"])
            ],
        }, POLICY, False)

        self.assertEqual(1, result["violationCount"])


if __name__ == "__main__":
    unittest.main()
