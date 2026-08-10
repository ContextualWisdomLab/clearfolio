#!/usr/bin/env python3
"""Execute the dependency-free viewer accessibility regression suite in CI."""

from __future__ import annotations

import shutil
import subprocess
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


class AccessibleAsyncViewerControlsTest(unittest.TestCase):
    """Exercise the shipped dependency-free viewer accessibility regressions."""

    def test_accessible_async_viewer_controls_have_complete_node_coverage(self) -> None:
        """Require nested-safe DOM behavior and its integration with the shipped demo."""

        node = shutil.which("node")
        self.assertIsNotNone(
            node,
            "Node.js is required for executable viewer accessibility tests",
        )
        node_path = node if node is not None else ""

        version = subprocess.run(
            [node_path, "--version"],
            cwd=REPOSITORY_ROOT,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        major = int(version.removeprefix("v").split(".", 1)[0])
        self.assertGreaterEqual(
            major,
            22,
            f"Node.js 22 or newer is required, found {version}",
        )

        result = subprocess.run(
            [
                node_path,
                "--test",
                "--experimental-test-coverage",
                "--test-coverage-include=src/main/resources/static/assets/viewer/dom-utils.js",
                "--test-coverage-lines=100",
                "--test-coverage-branches=100",
                "--test-coverage-functions=100",
                "src/test/js/dom-utils.test.mjs",
                "src/test/js/demo-integration.test.mjs",
            ],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertEqual(
            result.returncode,
            0,
            (
                f"Node accessibility regression suite failed with {version}.\n"
                f"stdout:\n{result.stdout}\n"
                f"stderr:\n{result.stderr}"
            ),
        )


if __name__ == "__main__":
    unittest.main()
