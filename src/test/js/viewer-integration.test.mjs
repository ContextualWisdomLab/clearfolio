import assert from "node:assert/strict";
import test from "node:test";

import { MockElement } from "./mock-dom.mjs";

test("viewer.js preserves retry button child nodes via setBusyState", async () => {
  const elements = new Map([
    ["doc-meta", new MockElement("p")],
    ["live-status", new MockElement("div")],
    ["error", new MockElement("div")],
    ["error-title", new MockElement("h3")],
    ["error-message", new MockElement("p")],
    ["retry-btn", new MockElement("button")],
    ["open-json-link", new MockElement("a")],
    ["preview", new MockElement("div")],
  ]);

  const retryBtn = elements.get("retry-btn");
  retryBtn.textContent = "Refresh";
  retryBtn.setAttribute("aria-label", "Refresh the viewer");

  globalThis.document = {
    getElementById(id) {
      return elements.get(id);
    },
    querySelector(sel) {
      if (sel === 'meta[name="clearfolio-doc-id"]') {
        const meta = new MockElement("meta");
        meta.setAttribute("content", "12345678-1234-1234-1234-123456789012");
        return meta;
      }
      return null;
    },
    createElement(tagName) {
      return new MockElement(tagName);
    },
  };

  globalThis.window = {
    location: { origin: "http://localhost", search: "?docId=12345678-1234-1234-1234-123456789012" },
    setTimeout(cb) {
      // Don't execute cb immediately to avoid infinite loop of polling
    },
  };
  globalThis.AbortController = class AbortController {
    constructor() { this.signal = { aborted: false }; }
    abort() { this.signal.aborted = true; }
  };
  globalThis.URLSearchParams = class URLSearchParams {
    constructor(s) { this.s = s; }
    get(k) {
        if (k === 'docId') return '12345678-1234-1234-1234-123456789012';
        return null;
    }
  };

  let resolveStatusFetch;
  let statusFetchCount = 0;
  globalThis.fetch = () => new Promise(resolve => {
    statusFetchCount += 1;
    resolveStatusFetch = resolve;
  });

  const moduleUrl = new URL(
    "../../main/resources/static/assets/viewer/viewer.js",
    import.meta.url,
  );
  moduleUrl.searchParams.set("integration", String(Date.now()));

  // start() will be called on init, wait for it
  await import(moduleUrl.href);
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(statusFetchCount, 1, "fetch must be called");
  assert.equal(retryBtn.disabled, true, "retry btn should be disabled");
  assert.equal(retryBtn.textContent, "Refreshing...", "retry btn text should be Refreshing...");
  assert.equal(retryBtn.getAttribute("aria-busy"), "true", "retry btn aria-busy should be true");
  assert.equal(retryBtn.getAttribute("aria-label"), "Refreshing... Refresh the viewer");

  // Mocking the successful fetch of status (but failed format/etc to stop polling)
  resolveStatusFetch({
    ok: true,
    headers: {
      get() { return "application/json"; }
    },
    async json() {
      return { status: "FAILED" };
    },
    status: 200
  });

  await new Promise(resolve => setImmediate(resolve));
  await new Promise(resolve => setImmediate(resolve)); // extra cycles for promise resolution

  // When failed, showError is called and should restore the retry btn
  assert.equal(retryBtn.disabled, false);
  assert.equal(retryBtn.textContent, "Refresh");
  assert.equal(retryBtn.getAttribute("aria-busy"), null);
  assert.equal(retryBtn.getAttribute("aria-label"), "Refresh the viewer");

  // Verify showError behavior
  const errorEl = elements.get("error");
  const errorMsg = elements.get("error-message");
  assert.equal(errorEl.hidden, false);
  assert.equal(errorMsg.textContent, "Preview is not available. Status: FAILED");
});
