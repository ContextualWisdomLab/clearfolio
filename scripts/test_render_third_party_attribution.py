#!/usr/bin/env python3
"""Unit and repository-contract tests for third-party attribution evidence."""

from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from render_third_party_attribution import render_markdown


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
POM_PATH = REPOSITORY_ROOT / "pom.xml"
SBOM_PATH = (
    REPOSITORY_ROOT
    / "docs"
    / "qa"
    / "evidence"
    / "2026-07-02-krw2b-sale-readiness"
    / "sbom-cyclonedx.json"
)
ATTRIBUTION_PATH = (
    REPOSITORY_ROOT / "docs" / "legal" / "2026-07-03-third-party-attribution.md"
)
NETTY_VERSION_PATTERN = re.compile(
    r"<netty\.version>\s*([^<\s]+)\s*</netty\.version>"
)


def component(group: str, name: str, version: str, license_id: str, purl: str) -> dict:
    """Build one compact CycloneDX component fixture for renderer tests."""
    return {
        "group": group,
        "name": name,
        "version": version,
        "purl": purl,
        "licenses": [{"license": {"id": license_id}}],
    }


def managed_netty_version() -> str:
    """Read the reviewed Netty family version from the trusted project POM."""
    matches = NETTY_VERSION_PATTERN.findall(POM_PATH.read_text(encoding="utf-8"))
    if len(matches) != 1:
        raise AssertionError("pom.xml must declare exactly one non-blank netty.version property")
    return matches[0]


class ThirdPartyAttributionTest(unittest.TestCase):
    """Protect rendering behavior and buyer-evidence dependency consistency."""

    def test_renders_sorted_component_table_and_summary(self) -> None:
        """Render deterministic summary metadata and sorted component rows."""
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
        self.assertLess(
            markdown.index("com.example:alpha"),
            markdown.index("org.springframework:spring-core"),
        )
        self.assertIn(
            "| com.example:alpha | 1.0.0 | MIT | "
            "`pkg:maven/com.example/alpha@1.0.0?type=jar` |",
            markdown,
        )
        self.assertIn(
            "| org.springframework:spring-core | 6.2.7 | Apache-2.0 | "
            "`pkg:maven/org.springframework/spring-core@6.2.7?type=jar` |",
            markdown,
        )

    def test_marks_missing_license_metadata_for_release_review(self) -> None:
        """Surface components whose SBOM license metadata needs human review."""
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

    def test_buyer_evidence_tracks_reviewed_netty_security_line(self) -> None:
        """Require the generated SBOM graph and attribution to match Maven."""
        expected_version = managed_netty_version()
        sbom_text = SBOM_PATH.read_text(encoding="utf-8")
        sbom = json.loads(sbom_text)
        netty_components = [
            item
            for item in sbom.get("components", [])
            if item.get("group") == "io.netty"
        ]

        self.assertGreater(
            len(netty_components),
            0,
            "the buyer SBOM must retain the resolved Netty runtime family",
        )
        self.assertEqual(
            {expected_version},
            {str(item.get("version", "")) for item in netty_components},
            "every resolved Netty module must match pom.xml netty.version",
        )
        component_refs = set()
        for item in netty_components:
            coordinate = f"io.netty:{item.get('name', '<unknown>')}"
            purl = str(item.get("purl", ""))
            bom_ref = str(item.get("bom-ref", ""))
            self.assertIn(
                f"@{expected_version}",
                purl,
                f"{coordinate} purl must identify the reviewed Netty version",
            )
            self.assertIn(
                f"@{expected_version}",
                bom_ref,
                f"{coordinate} bom-ref must identify the reviewed Netty version",
            )
            component_refs.add(bom_ref)

        dependency_refs = set()
        for dependency in sbom.get("dependencies", []):
            dependency_ref = str(dependency.get("ref", ""))
            if "pkg:maven/io.netty/" in dependency_ref:
                dependency_refs.add(dependency_ref)
            dependency_refs.update(
                str(item)
                for item in dependency.get("dependsOn", [])
                if "pkg:maven/io.netty/" in str(item)
            )
        self.assertEqual(
            component_refs,
            dependency_refs,
            "every Netty dependency edge must resolve to one current component ref",
        )
        self.assertNotIn(
            "4.1.135.Final",
            sbom_text,
            "the historical Spring-managed Netty line must be absent from the SBOM",
        )

        actual_attribution = ATTRIBUTION_PATH.read_text(encoding="utf-8")
        self.assertEqual(
            render_markdown(sbom),
            actual_attribution,
            "buyer attribution must be regenerated from the committed SBOM",
        )
        netty_rows = [
            line
            for line in actual_attribution.splitlines()
            if line.startswith("| io.netty:")
        ]
        self.assertEqual(
            len(netty_components),
            len(netty_rows),
            "attribution must contain one row for every resolved Netty component",
        )
        self.assertTrue(
            all(f"| {expected_version} |" in row for row in netty_rows),
            "every attribution row must use the reviewed Netty version",
        )
        self.assertNotIn(
            "4.1.135.Final",
            actual_attribution,
            "the historical Netty line must be absent from buyer attribution",
        )


if __name__ == "__main__":
    unittest.main()
