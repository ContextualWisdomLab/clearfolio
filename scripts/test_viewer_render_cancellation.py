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

function makeHarness() {
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

  return { context, elements };
}

async function flushMicrotasks(turns = 8) {
  for (let index = 0; index < turns; index += 1) {
    await Promise.resolve();
  }
}

async function activeRenderCancellationIsPropagated() {
  const { context, elements } = makeHarness();
  let resolveRender;
  let renderCancelCalls = 0;
  let documentDestroyCalls = 0;
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
          return {
            promise: renderPromise,
            cancel() {
              renderCancelCalls += 1;
              resolveRender();
            },
          };
        },
      };
    },
    async destroy() {
      documentDestroyCalls += 1;
    },
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

  await flushMicrotasks();
  controller.abort();
  await flushMicrotasks();
  const cancelCallsObservedOnAbort = renderCancelCalls;
  if (renderCancelCalls === 0) {
    resolveRender();
  }
  await rendering;

  assert.equal(
    cancelCallsObservedOnAbort,
    1,
    "aborting an active PDF render must call RenderTask.cancel() immediately",
  );
  assert.equal(documentDestroyCalls, 1, "the loaded PDF document must be destroyed exactly once");
  assert.equal(
    elements.get("preview").children.length,
    0,
    "a superseded PDF render must not publish canvas or metadata",
  );
}

async function loadingCancellationIsPropagated() {
  const { context, elements } = makeHarness();
  let resolveLoading;
  let loadingDestroyCalls = 0;
  let documentDestroyCalls = 0;
  const pdfDocument = {
    numPages: 1,
    async getPage() {
      throw new Error("aborted loading must not request a page");
    },
    async destroy() {
      documentDestroyCalls += 1;
    },
  };
  const loadingPromise = new Promise(resolve => {
    resolveLoading = resolve;
  });
  const loadingTask = {
    promise: loadingPromise,
    async destroy() {
      loadingDestroyCalls += 1;
      resolveLoading(pdfDocument);
    },
  };
  context.__pdfJs = {
    GlobalWorkerOptions: {},
    getDocument() {
      return loadingTask;
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

  await flushMicrotasks();
  controller.abort();
  await flushMicrotasks();
  const destroyCallsObservedOnAbort = loadingDestroyCalls;
  if (loadingDestroyCalls === 0) {
    resolveLoading(pdfDocument);
  }
  await rendering;

  assert.equal(
    destroyCallsObservedOnAbort,
    1,
    "aborting PDF loading must call PDFDocumentLoadingTask.destroy() immediately",
  );
  assert.equal(documentDestroyCalls, 1, "a loaded proxy produced during cancellation must be destroyed");
  assert.equal(
    elements.get("preview").children.length,
    0,
    "an aborted loading task must not publish preview DOM",
  );
}

(async () => {
  await activeRenderCancellationIsPropagated();
  await loadingCancellationIsPropagated();
})().catch(error => {
  console.error(error.stack || String(error));
  process.exitCode = 1;
});
"""


def test_pdfjs_tasks_receive_abort_cancellation() -> None:
    """Abort in-flight PDF.js work and require active resource cancellation."""

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
