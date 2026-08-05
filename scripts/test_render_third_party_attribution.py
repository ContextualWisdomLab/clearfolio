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
NETTY_GROUP = "io.netty"
RETIRED_NETTY_VERSION = "4.1.135.Final"


def component(group: str, name: str, version: str, license_id: str, purl: str) -> dict:
    """Build one minimal CycloneDX component fixture."""
    return {
        "group": group,
        "name": name,
        "version": version,
        "purl": purl,
        "licenses": [{"license": {"id": license_id}}],
    }


def trusted_netty_version() -> str:
    """Read the single literal root ``netty.version`` without parsing XML.

    The POM is trusted repository source, but the test deliberately avoids an
    XML parser so generated dependency evidence cannot introduce an XML entity
    or expansion surface into the verification path.
    """
    pom_text = POM_PATH.read_text(encoding="utf-8")
    matches = re.findall(r"<netty\.version>([^<]+)</netty\.version>", pom_text)
    if len(matches) != 1:
        raise AssertionError(
            "pom.xml must declare exactly one literal netty.version property"
        )
    version = matches[0].strip()
    if not version:
        raise AssertionError("pom.xml netty.version must not be blank")
    return version


class ThirdPartyAttributionTest(unittest.TestCase):
    """Verify renderer behavior and committed buyer-evidence coherence."""

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

    def test_committed_netty_evidence_matches_the_trusted_pom(self) -> None:
        """Reject empty, mixed, stale, hand-edited, or drifted Netty evidence."""
        expected_version = trusted_netty_version()
        sbom_text = SBOM_PATH.read_text(encoding="utf-8")
        attribution_text = ATTRIBUTION_PATH.read_text(encoding="utf-8")
        sbom = json.loads(sbom_text)
        components = sbom.get("components")

        self.assertIsInstance(components, list)
        self.assertTrue(components, "committed CycloneDX component set is empty")
        netty_components = [
            item for item in components if item.get("group") == NETTY_GROUP
        ]
        self.assertTrue(netty_components, "resolved Netty component family is empty")

        netty_refs: set[str] = set()
        for item in netty_components:
            component_name = str(item.get("name", "<missing-name>"))
            self.assertEqual(
                expected_version,
                item.get("version"),
                f"Netty component version drifted: {component_name}",
            )
            expected_marker = f"@{expected_version}"
            purl = str(item.get("purl", ""))
            bom_ref = str(item.get("bom-ref", ""))
            self.assertIn(
                expected_marker,
                purl,
                f"Netty purl version drifted: {component_name}",
            )
            self.assertIn(
                expected_marker,
                bom_ref,
                f"Netty bom-ref version drifted: {component_name}",
            )
            self.assertTrue(
                bom_ref,
                f"Netty component has no bom-ref: {component_name}",
            )
            netty_refs.add(bom_ref)

        dependency_netty_refs: set[str] = set()
        for dependency in sbom.get("dependencies", []):
            dependency_ref = str(dependency.get("ref", ""))
            child_refs = {
                str(child_ref) for child_ref in dependency.get("dependsOn", [])
            }
            for candidate_ref in {dependency_ref, *child_refs}:
                if "pkg:maven/io.netty/" in candidate_ref:
                    dependency_netty_refs.add(candidate_ref)
        self.assertTrue(
            dependency_netty_refs,
            "CycloneDX dependency graph contains no Netty references",
        )
        self.assertTrue(
            dependency_netty_refs.issubset(netty_refs),
            "CycloneDX dependency graph references an uncommitted Netty component: "
            f"{sorted(dependency_netty_refs - netty_refs)}",
        )

        self.assertEqual(
            render_markdown(sbom),
            attribution_text,
            "third-party attribution was not generated from the committed SBOM",
        )
        self.assertNotIn(RETIRED_NETTY_VERSION, sbom_text)
        self.assertNotIn(RETIRED_NETTY_VERSION, attribution_text)


if __name__ == "__main__":
    unittest.main()
