#!/usr/bin/env python3
"""Execute the dependency-free viewer accessibility regression suite in CI."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def test_accessible_async_viewer_controls_have_complete_node_coverage() -> None:
    """Require nested-safe DOM behavior and its integration with the shipped demo."""

    node = shutil.which("node")
    assert node is not None, "Node.js is required for executable viewer accessibility tests"

    version = subprocess.run(
        [node, "--version"],
        cwd=REPOSITORY_ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    major = int(version.removeprefix("v").split(".", 1)[0])
    assert major >= 20, f"Node.js 20 or newer is required, found {version}"

    result = subprocess.run(
        [
            node,
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

    assert result.returncode == 0, (
        f"Node accessibility regression suite failed with {version}.\n"
        f"stdout:\n{result.stdout}\n"
        f"stderr:\n{result.stderr}"
    )
