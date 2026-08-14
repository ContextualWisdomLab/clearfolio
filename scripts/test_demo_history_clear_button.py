#!/usr/bin/env python3
"""Exercise session-history clear-button state in a real JavaScript runtime."""

from __future__ import annotations

import shutil
import subprocess
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEMO_SOURCE = REPOSITORY_ROOT / "src/main/resources/static/assets/viewer/demo.js"

NODE_HARNESS = r"""
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

function makeElement(tagName = "div") {
  return {
    tagName: tagName.toUpperCase(),
    textContent: "",
    hidden: false,
    disabled: false,
    className: "",
    children: [],
    append(...children) {
      this.children.push(...children);
    },
    appendChild(child) {
      this.children.push(child);
      return child;
    },
  };
}

const elements = new Map([
  ["history-body", makeElement()],
  ["empty-history", makeElement()],
  ["clear-history-btn", makeElement("button")],
]);
const context = {
  console,
  document: {
    createElement(tagName) {
      return makeElement(tagName);
    },
  },
  el: {
    historyBody: elements.get("history-body"),
    emptyHistory: elements.get("empty-history"),
    clearHistoryBtn: elements.get("clear-history-btn"),
  },
  createActionButton() {
    return makeElement("button");
  },
  createLink() {
    return makeElement("a");
  },
  setBusyState() {
    return () => {};
  },
  renderRecoveryEvidence() {},
  openJobDetail() {},
  openJsonDocument() {},
};
context.globalThis = context;
vm.createContext(context);

const source = fs.readFileSync(process.argv[1], "utf8");
const functionStart = source.indexOf("function renderHistory(");
const functionEnd = source.indexOf("\nfunction addDetailRow(", functionStart);
assert.notEqual(functionStart, -1, "renderHistory must remain discoverable");
assert.notEqual(functionEnd, -1, "renderHistory must retain a complete top-level boundary");
const renderHistorySource = source.slice(functionStart, functionEnd);
vm.runInContext(
  `${renderHistorySource}\n;globalThis.__test = { renderHistory };`,
  context,
  { filename: process.argv[1] },
);

const clearButton = elements.get("clear-history-btn");
const emptyHistory = elements.get("empty-history");

context.__test.renderHistory([]);
assert.equal(clearButton.disabled, true, "empty history must disable Clear");
assert.equal(emptyHistory.hidden, false, "empty-state copy must remain visible");

context.__test.renderHistory([
  {
    fileName: "evidence.pdf",
    status: "SUCCEEDED",
    submittedAt: "2026-08-14T08:00:00Z",
  },
]);
assert.equal(clearButton.disabled, false, "non-empty history must enable Clear");
assert.equal(emptyHistory.hidden, true, "empty-state copy must be hidden for history rows");
assert.equal(elements.get("history-body").children.length, 1, "one row must render");
"""


class DemoHistoryClearButtonTest(unittest.TestCase):
    """Validate dynamic empty/non-empty history behavior through Node.js."""

    def test_clear_button_tracks_rendered_history(self) -> None:
        """Require the Clear control to follow the current history collection."""

        node = shutil.which("node")
        self.assertIsNotNone(node, "Node.js is required for the demo UI regression")
        assert node is not None

        result = subprocess.run(
            [node, "-e", NODE_HARNESS, str(DEMO_SOURCE)],
            cwd=REPOSITORY_ROOT,
            capture_output=True,
            check=False,
            text=True,
            timeout=15,
        )

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
