#!/usr/bin/env python3
"""Unit tests for the Clearfolio third-party attribution renderer."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from render_third_party_attribution import render_markdown


def component(group: str, name: str, version: str, license_id: str, purl: str) -> dict:
    return {
        "group": group,
        "name": name,
        "version": version,
        "purl": purl,
        "licenses": [{"license": {"id": license_id}}],
    }


class ThirdPartyAttributionTest(unittest.TestCase):

    def test_renders_sorted_component_table_and_summary(self) -> None:
        markdown = render_markdown({
            "bomFormat": "CycloneDX",
            "specVersion": "1.6",
            "components": [
                component(
                    "org.springframework",
                    "spring-core",
                    "6.2.7",
                    "Apache-2.0",
                    "pkg:maven/org.springframework/spring-core@6.2.7?type=jar",
                ),
                component(
                    "com.example",
                    "alpha",
                    "1.0.0",
                    "MIT",
                    "pkg:maven/com.example/alpha@1.0.0?type=jar",
                ),
            ],
        })

        self.assertIn("# Third-Party Attribution", markdown)
        self.assertIn("SBOM format: CycloneDX 1.6", markdown)
        self.assertIn("Component count: 2", markdown)
        self.assertLess(markdown.index("com.example:alpha"), markdown.index("org.springframework:spring-core"))
        self.assertIn("| com.example:alpha | 1.0.0 | MIT | `pkg:maven/com.example/alpha@1.0.0?type=jar` |", markdown)
        self.assertIn("| org.springframework:spring-core | 6.2.7 | Apache-2.0 | `pkg:maven/org.springframework/spring-core@6.2.7?type=jar` |", markdown)

    def test_marks_missing_license_metadata_for_release_review(self) -> None:
        markdown = render_markdown({
            "components": [
                {
                    "name": "unknown-license",
                    "version": "1",
                    "purl": "pkg:maven/example/unknown-license@1?type=jar",
                }
            ],
        })

        self.assertIn("NOASSERTION", markdown)


if __name__ == "__main__":
    unittest.main()
