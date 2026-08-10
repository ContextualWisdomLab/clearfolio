#!/usr/bin/env python3
"""Exercise viewer PDF rendering cancellation in a real JavaScript runtime."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
VIEWER_SOURCE = REPOSITORY_ROOT / "src/main/resources/static/assets/viewer/viewer.js"

NODE_HARNESS = r"""
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

function makeElement(tagName = "div") {
  return {
    tagName: tagName.toUpperCase(),
    hidden: false,
    disabled: false,
    textContent: "",
    className: "",
    href: "",
    target: "",
    rel: "",
    clientWidth: 800,
    children: [],
    style: {},
    attributes: new Map(),
    setAttribute(name, value) {
      this.attributes.set(name, String(value));
    },
    getAttribute(name) {
      return this.attributes.get(name) ?? null;
    },
    addEventListener() {},
    appendChild(child) {
      this.children.push(child);
      return child;
    },
    querySelector() {
      return null;
    },
    querySelectorAll() {
      return [];
    },
    remove() {},
    focus() {},
    getContext() {
      return {};
    },
  };
}

(async () => {
  const elements = new Map();
  const ids = [
    "doc-meta",
    "live-status",
    "error",
    "error-title",
    "error-message",
    "retry-btn",
    "open-json-link",
    "preview",
  ];
  for (const id of ids) {
    elements.set(id, makeElement());
  }

  const documentStub = {
    getElementById(id) {
      return elements.get(id) ?? null;
    },
    querySelector() {
      return null;
    },
    createElement(tagName) {
      return makeElement(tagName);
    },
  };

  const windowStub = {
    location: {
      origin: "https://viewer.example.test",
      search: "",
    },
    setTimeout() {},
    open() {
      return null;
    },
  };

  const context = {
    AbortController,
    URL,
    URLSearchParams,
    console,
    document: documentStub,
    window: windowStub,
  };
  context.globalThis = context;
  vm.createContext(context);

  const viewerPath = process.argv[1];
  const viewerSource = fs.readFileSync(viewerPath, "utf8");
  vm.runInContext(
    `${viewerSource}\n;globalThis.__viewerTest = { renderPdfInline };`,
    context,
    { filename: viewerPath },
  );

  let resolveRender;
  const renderPromise = new Promise(resolve => {
    resolveRender = resolve;
  });
  const pdfDocument = {
    numPages: 1,
    async getPage() {
      return {
        getViewport({ scale }) {
          return { width: 100 * scale, height: 200 * scale };
        },
        render() {
          return { promise: renderPromise };
        },
      };
    },
    async destroy() {},
  };
  context.__pdfJs = {
    GlobalWorkerOptions: {},
    getDocument() {
      return { promise: Promise.resolve(pdfDocument) };
    },
  };
  vm.runInContext(
    "pdfJsModulePromise = Promise.resolve(globalThis.__pdfJs);",
    context,
  );

  const controller = new AbortController();
  const rendering = context.__viewerTest.renderPdfInline(
    "/artifacts/document.pdf",
    controller.signal,
  );

  // Let getDocument/getPage reach the deliberately unresolved render promise.
  await Promise.resolve();
  await Promise.resolve();
  controller.abort();
  resolveRender();
  await rendering;

  const preview = elements.get("preview");
  assert.equal(
    preview.children.length,
    0,
    "a superseded PDF render must not publish canvas or metadata",
  );
})().catch(error => {
  console.error(error.stack || String(error));
  process.exitCode = 1;
});
"""


def test_superseded_pdf_render_does_not_publish() -> None:
    """Abort an in-flight render and require zero stale DOM publication."""

    node = shutil.which("node")
    assert node is not None, "Node.js is required for the viewer runtime regression"

    result = subprocess.run(
        [node, "-e", NODE_HARNESS, str(VIEWER_SOURCE)],
        cwd=REPOSITORY_ROOT,
        capture_output=True,
        check=False,
        text=True,
        timeout=15,
    )

    assert result.returncode == 0, result.stdout + result.stderr
