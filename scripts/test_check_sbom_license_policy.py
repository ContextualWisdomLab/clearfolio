#!/usr/bin/env python3
"""Unit tests for the Clearfolio SBOM license policy checker."""

from __future__ import annotations

import json
import tempfile
import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_sbom_license_policy import evaluate, load_json, main


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


class LoadJsonTest(unittest.TestCase):

    def test_load_json_round_trips_file_contents(self) -> None:
        payload = {"bomFormat": "CycloneDX", "components": []}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "sbom.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            self.assertEqual(payload, load_json(path))


class MainCliTest(unittest.TestCase):
    """End-to-end tests for the CLI entrypoint (argument parsing, IO, exit codes)."""

    def _write(self, directory: Path, name: str, payload: dict) -> Path:
        path = directory / name
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path

    def test_main_returns_zero_and_writes_summary_for_clean_sbom(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            sbom = self._write(tmp_path, "sbom.json", {
                "bomFormat": "CycloneDX",
                "specVersion": "1.6",
                "components": [
                    component("allowed", "pkg:maven/example/allowed@1?type=jar", ["MIT"])
                ],
            })
            policy = self._write(tmp_path, "policy.json", POLICY)
            summary = tmp_path / "summary.json"

            exit_code = main([
                "--sbom", str(sbom),
                "--policy", str(policy),
                "--summary", str(summary),
            ])

            self.assertEqual(0, exit_code)
            self.assertTrue(summary.exists())
            written = json.loads(summary.read_text(encoding="utf-8"))
            self.assertEqual(0, written["violationCount"])
            self.assertEqual(1, written["allowedCount"])

    def test_main_returns_two_when_violations_present(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            sbom = self._write(tmp_path, "sbom.json", {
                "components": [
                    component("blocked", "pkg:maven/example/blocked@1?type=jar", ["GPL-3.0"])
                ],
            })
            policy = self._write(tmp_path, "policy.json", POLICY)

            exit_code = main(["--sbom", str(sbom), "--policy", str(policy)])

            self.assertEqual(2, exit_code)

    def test_main_require_no_review_flag_blocks_review_component(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            sbom = self._write(tmp_path, "sbom.json", {
                "components": [
                    component("review", "pkg:maven/example/review@1?type=jar", ["LGPL-2.1"])
                ],
            })
            policy = self._write(tmp_path, "policy.json", POLICY)

            exit_code = main([
                "--sbom", str(sbom),
                "--policy", str(policy),
                "--require-no-review",
            ])

            self.assertEqual(2, exit_code)


if __name__ == "__main__":
    unittest.main()
