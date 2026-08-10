#!/usr/bin/env python3
"""Exercise active PDF.js resource cancellation in a real JavaScript runtime."""

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
    setAttribute(name, value) { this.attributes.set(name, String(value)); },
    getAttribute(name) { return this.attributes.get(name) ?? null; },
    addEventListener() {},
    appendChild(child) { this.children.push(child); return child; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    remove() {},
    focus() {},
    getContext() { return {}; },
  };
}

function createViewerContext() {
  const elements = new Map();
  for (const id of [
    "doc-meta", "live-status", "error", "error-title", "error-message",
    "retry-btn", "open-json-link", "preview",
  ]) {
    elements.set(id, makeElement());
  }

  const context = {
    AbortController,
    URL,
    URLSearchParams,
    console,
    document: {
      getElementById(id) { return elements.get(id) ?? null; },
      querySelector() { return null; },
      createElement(tagName) { return makeElement(tagName); },
    },
    window: {
      location: { origin: "https://viewer.example.test", search: "" },
      setTimeout() {},
      open() { return null; },
    },
  };
  context.globalThis = context;
  vm.createContext(context);
  const viewerSource = fs.readFileSync(process.argv[1], "utf8");
  vm.runInContext(
    `${viewerSource}\n;globalThis.__viewerTest = { renderPdfInline };`,
    context,
    { filename: process.argv[1] },
  );
  return context;
}

async function assertRenderTaskIsCancelled(context) {
  let resolveRender;
  let markRenderStarted;
  const renderPromise = new Promise(resolve => { resolveRender = resolve; });
  const renderStarted = new Promise(resolve => { markRenderStarted = resolve; });
  let cancelCalls = 0;
  let documentDestroyCalls = 0;
  const renderTask = {
    promise: renderPromise,
    cancel() { cancelCalls += 1; },
  };
  const pdfDocument = {
    numPages: 1,
    async getPage() {
      return {
        getViewport({ scale }) { return { width: 100 * scale, height: 200 * scale }; },
        render() {
          markRenderStarted();
          return renderTask;
        },
      };
    },
    async destroy() { documentDestroyCalls += 1; },
  };
  context.__pdfJs = {
    GlobalWorkerOptions: {},
    getDocument() {
      return { promise: Promise.resolve(pdfDocument), destroy() {} };
    },
  };
  vm.runInContext("pdfJsModulePromise = Promise.resolve(globalThis.__pdfJs);", context);

  const controller = new AbortController();
  const rendering = context.__viewerTest.renderPdfInline(
    "/artifacts/document.pdf",
    controller.signal,
  );
  await renderStarted;
  controller.abort();

  assert.equal(cancelCalls, 1, "supersession must actively cancel the current PDF.js RenderTask");
  resolveRender();
  await rendering;
  assert.equal(documentDestroyCalls, 1, "completed render cleanup must still destroy the PDF document");
}

async function assertLoadingTaskIsDestroyed(context) {
  let markLoadingStarted;
  const loadingStarted = new Promise(resolve => { markLoadingStarted = resolve; });
  let loadingDestroyCalls = 0;
  const neverSettles = new Promise(() => {});
  context.__pdfJs = {
    GlobalWorkerOptions: {},
    getDocument() {
      markLoadingStarted();
      return {
        promise: neverSettles,
        destroy() { loadingDestroyCalls += 1; return Promise.resolve(); },
      };
    },
  };
  vm.runInContext("pdfJsModulePromise = Promise.resolve(globalThis.__pdfJs);", context);

  const controller = new AbortController();
  void context.__viewerTest.renderPdfInline(
    "/artifacts/document.pdf",
    controller.signal,
  );
  await loadingStarted;
  controller.abort();
  await Promise.resolve();

  assert.equal(
    loadingDestroyCalls,
    1,
    "supersession must destroy an in-flight PDFDocumentLoadingTask when the API is available",
  );
}

(async () => {
  await assertRenderTaskIsCancelled(createViewerContext());
  await assertLoadingTaskIsDestroyed(createViewerContext());
})().catch(error => {
  console.error(error.stack || String(error));
  process.exitCode = 1;
});
"""


def test_pdfjs_tasks_are_actively_cancelled_on_supersession() -> None:
    """Require active RenderTask cancellation and loading-task destruction."""

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
