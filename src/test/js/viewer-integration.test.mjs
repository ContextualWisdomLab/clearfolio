import assert from "node:assert/strict";
import test from "node:test";
import { MockElement } from "./mock-dom.mjs";

test("viewer.js poll lifecycle implements normal→busy→success/error/retry contracts and duplicate-request prevention", async () => {
  const elements = new Map([
    ["doc-meta", new MockElement()],
    ["live-status", new MockElement()],
    ["error", new MockElement()],
    ["error-title", new MockElement()],
    ["error-message", new MockElement()],
    ["retry-btn", new MockElement("button")],
    ["open-json-link", new MockElement("a")],
    ["preview", new MockElement()],
  ]);

  const retryBtn = elements.get("retry-btn");
  retryBtn.textContent = "Retry Validation";

  const preview = elements.get("preview");
  preview.querySelectorAll = () => [];
  preview.querySelector = () => null;

  globalThis.document = {
    getElementById(id) {
      const el = elements.get(id);
      if (el) return el;
      return new MockElement("div");
    },
    createElement(tagName) {
      return new MockElement(tagName);
    },
    querySelector(selector) {
      if (selector === "meta[name=\"clearfolio-initial-state\"]") {
        return null;
      }
      if (selector === "meta[name=\"clearfolio-doc-id\"]") {
        const meta = new MockElement("meta");
        meta.setAttribute("content", "123e4567-e89b-12d3-a456-426614174000");
        return meta;
      }
      if (selector === "meta[name=\"_csrf\"]") {
        const meta = new MockElement("meta");
        meta.setAttribute("content", "mock-csrf-token");
        return meta;
      }
      if (selector === "meta[name=\"_csrf_header\"]") {
        const meta = new MockElement("meta");
        meta.setAttribute("content", "X-CSRF-TOKEN");
        return meta;
      }
      return null;
    }
  };

  let resolveFetch;
  let rejectFetch;
  let fetchCount = 0;
  globalThis.fetch = async (url) => new Promise((resolve, reject) => {
    fetchCount++;
    resolveFetch = resolve;
    rejectFetch = reject;
  });

  const pathParts = ["", "viewer", "123e4567-e89b-12d3-a456-426614174000"];
  globalThis.window = {
    location: {
      pathname: pathParts.join("/"),
      search: "",
      origin: "http://localhost:8080"
    },
    URLSearchParams: class {
      constructor() {}
      get() { return null; }
    },
    setTimeout: (cb, delay) => {
        // mock setTimeout
    }
  };

  const originalURL = globalThis.URL || URL;
  globalThis.URL = class MockURL {
    constructor(urlStr, baseStr) {
      if (!urlStr) throw new TypeError("Invalid URL");
      this.href = typeof baseStr === 'string' ? `${baseStr.replace(/\/$/, '')}/${urlStr.replace(/^\//, '')}` : urlStr;
      const parts = this.href.split("://");
      this.protocol = parts.length > 1 ? parts[0] + ":" : "http:";
      this.origin = baseStr || "http://localhost:8080";
      this.username = "";
      this.password = "";
    }
  };

  const moduleUrl = new originalURL(
    "../../main/resources/static/assets/viewer/viewer.js",
    import.meta.url,
  );

  await import(moduleUrl.href + "?integration=" + Date.now());
  await new Promise(resolve => setTimeout(resolve, 10));

  assert.equal(fetchCount, 1);

  // Resolve first fetch to fail (500)
  resolveFetch({
    ok: false,
    status: 500,
    headers: { get: () => "application/json" },
    json: async () => ({})
  });
  await new Promise(resolve => setTimeout(resolve, 10));

  // Verify error state
  assert.equal(elements.get("error").hidden, false);
  assert.equal(retryBtn.disabled, false);
  assert.equal(retryBtn.textContent, "Retry Validation");
  assert.equal(retryBtn.getAttribute("aria-busy"), null);

  // Click retry button
  retryBtn.dispatchEvent({ type: "click" });

  assert.equal(fetchCount, 2);

  // Duplicate click
  const secondClickResult = retryBtn.dispatchEvent({ type: "click" });
  assert.equal(secondClickResult, false); // mock-dom returns false if disabled

  assert.equal(fetchCount, 2);
  assert.equal(retryBtn.disabled, true);
  assert.equal(retryBtn.textContent, "Refreshing...");
  assert.equal(retryBtn.getAttribute("aria-busy"), "true");

  // Now resolve the second fetch with success
  resolveFetch({
    ok: true,
    status: 200,
    headers: { get: () => "application/json" },
    json: async () => ({ status: "SUCCEEDED" })
  });
  await new Promise(resolve => setTimeout(resolve, 10));

  assert.equal(fetchCount, 3);

  resolveFetch({
    ok: true,
    status: 200,
    headers: { get: () => "application/json" },
    json: async () => ({ previewResourcePath: "test.png" })
  });
  await new Promise(resolve => setTimeout(resolve, 10));

  assert.equal(retryBtn.disabled, false);
  assert.equal(retryBtn.textContent, "Retry Validation");
  assert.equal(retryBtn.getAttribute("aria-busy"), null);
  assert.equal(elements.get("live-status").textContent, "Ready.");
});
