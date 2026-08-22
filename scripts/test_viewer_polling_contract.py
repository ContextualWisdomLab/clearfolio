#!/usr/bin/env python3
"""Guard the viewer polling loop against retained recursive promise chains."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
VIEWER_SOURCE = (
    REPOSITORY_ROOT
    / "src"
    / "main"
    / "resources"
    / "static"
    / "assets"
    / "viewer"
    / "viewer.js"
)


class ViewerPollingContractTest(unittest.TestCase):
    """Verify that long-running conversion polling keeps constant promise depth."""

    def test_pending_status_retries_iteratively_without_recursive_poll(self) -> None:
        """Require delay-and-continue polling instead of awaited self-recursion."""

        source = VIEWER_SOURCE.read_text(encoding="utf-8")
        match = re.search(
            r"async function poll\(docId, abortSignal\) \{(?P<body>.*?)\n\}\n\nasync function init",
            source,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(match, "viewer poll() implementation must remain discoverable")
        body = match.group("body") if match is not None else ""

        self.assertIn(
            "while (!abortSignal.aborted)",
            body,
            "poll() must retry pending conversions with a constant-depth loop",
        )
        self.assertIn(
            "await new Promise(resolve => window.setTimeout(resolve, POLL_DELAY_MS));",
            body,
            "poll() must preserve the bounded delay between pending status reads",
        )
        self.assertNotIn(
            "await poll(docId, abortSignal)",
            body,
            "awaited self-recursion retains one promise frame per pending poll",
        )


if __name__ == "__main__":
    unittest.main()
