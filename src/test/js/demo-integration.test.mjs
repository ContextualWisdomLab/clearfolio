import assert from "node:assert/strict";
import { describe, test } from "node:test";

import { MockElement } from "./mock-dom.mjs";

const elementIds = [
  "upload-form",
  "file-input",
  "submit-btn",
  "demo-status",
  "demo-error",
  "demo-error-message",
  "demo-error-title",
  "load-demo-data-btn",
  "history-body",
  "empty-history",
  "history-title",
  "clear-history-btn",
  "kpi-total",
  "kpi-ready",
  "kpi-success-rate",
  "kpi-p95",
  "kpi-export-count",
  "kpi-export-latest",
  "kpi-export-subject",
  "kpi-export-jobs",
  "kpi-export-status",
  "refresh-evidence-btn",
  "recovery-needs-action",
  "recovery-retry-ready",
  "recovery-last-action",
  "recovery-latest-inspected",
  "recovery-status",
  "job-detail",
  "job-detail-caption",
  "job-detail-body",
  "retry-job-btn",
];

describe("buyer-demo session history", { concurrency: false }, () => {
test("the executable demo renders inert actions and blocks repeated status activation", async () => {
  const elements = new Map(elementIds.map(id => [id, new MockElement()]));
  const fileName = "<img src=x onerror=alert(1)>.pdf";
  const history = [{
    fileName,
    status: "SUCCEEDED",
    submittedAt: "2026-08-05T00:00:00Z",
    jobId: "document-identifier",
    statusUrl: "/api/v1/convert/jobs/document-identifier",
  }];

  const historyTitle = elements.get("history-title");
  historyTitle.focus = function focusHistoryTitle() {
    globalThis.document.activeElement = historyTitle;
  };

  globalThis.document = {
    activeElement: null,
    getElementById(id) {
      return elements.get(id);
    },
    createElement(tagName) {
      return new MockElement(tagName);
    },
  };
  let confirmClear = true;
  globalThis.window = {
    confirm() {
      return confirmClear;
    },
    open() {
      return null;
    },
    setTimeout() {
      throw new Error("completed jobs must not schedule polling");
    },
  };
  globalThis.localStorage = {
    getItem() {
      return JSON.stringify(history);
    },
    setItem() {},
  };
  globalThis.fetch = async () => ({
    ok: false,
    headers: {
      get() {
        return "application/json";
      },
    },
    async json() {
      return null;
    },
  });

  const moduleUrl = new URL(
    "../../main/resources/static/assets/viewer/demo.js",
    import.meta.url,
  );
  moduleUrl.searchParams.set("integration", String(Date.now()));
  await import(moduleUrl.href);
  await new Promise(resolve => setImmediate(resolve));

  const rows = elements.get("history-body").childNodes;
  assert.equal(rows.length, 1);
  const [fileCell, statusCell, , actionsCell] = rows[0].childNodes;
  assert.equal(fileCell.textContent, fileName);
  assert.equal(fileCell.childNodes.length, 1);
  assert.equal(fileCell.childNodes[0].type, "text");
  assert.equal(statusCell.textContent, "SUCCEEDED");
  assert.equal(actionsCell.childNodes.length, 3);
  assert.equal(
    actionsCell.childNodes[0].getAttribute("aria-label"),
    `View details for ${fileName}`,
  );
  assert.equal(
    actionsCell.childNodes[1].getAttribute("aria-label"),
    `View status JSON for ${fileName}`,
  );
  assert.equal(
    actionsCell.childNodes[2].getAttribute("aria-label"),
    `Open viewer for ${fileName}`,
  );
  assert.equal(actionsCell.childNodes[2].href, "/viewer/document-identifier");
  assert.equal(elements.get("empty-history").hidden, true);
  assert.equal(elements.get("clear-history-btn").disabled, false);

  const statusButton = actionsCell.childNodes[1];
  const popupBody = new MockElement("body");
  const popup = {
    opener: {},
    document: {
      title: "",
      body: popupBody,
      createElement(tagName) {
        return new MockElement(tagName);
      },
    },
  };
  globalThis.window.open = () => popup;

  let resolveStatusFetch;
  let statusFetchCount = 0;
  globalThis.fetch = () => new Promise(resolve => {
    statusFetchCount += 1;
    resolveStatusFetch = resolve;
  });

  statusButton.dispatchEvent({ type: "click" });
  statusButton.dispatchEvent({ type: "click" });

  assert.equal(statusFetchCount, 1);
  assert.equal(statusButton.disabled, true);
  assert.equal(statusButton.textContent, "Loading status JSON...");
  assert.equal(statusButton.getAttribute("aria-busy"), "true");
  assert.equal(
    statusButton.getAttribute("aria-label"),
    `Loading status JSON... View status JSON for ${fileName}`,
  );
  assert.equal(popup.opener, null);
  assert.equal(popupBody.childNodes[0].textContent, "Loading...");

  resolveStatusFetch({
    ok: true,
    headers: {
      get() {
        return "application/json";
      },
    },
    async json() {
      return { status: "SUCCEEDED" };
    },
  });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(statusButton.disabled, false);
  assert.equal(statusButton.textContent, "Status JSON");
  assert.equal(statusButton.getAttribute("aria-busy"), null);
  assert.equal(
    statusButton.getAttribute("aria-label"),
    `View status JSON for ${fileName}`,
  );
  assert.match(popupBody.childNodes[0].textContent, /"status": "SUCCEEDED"/);

  const clearHistoryBtn = elements.get("clear-history-btn");
  const emptyHistory = elements.get("empty-history");
  const historyBody = elements.get("history-body");

  confirmClear = false;
  globalThis.document.activeElement = clearHistoryBtn;
  clearHistoryBtn.dispatchEvent({ type: "click" });
  assert.equal(clearHistoryBtn.disabled, false);
  assert.equal(emptyHistory.hidden, true);
  assert.equal(historyBody.childNodes.length, 1);
  assert.equal(globalThis.document.activeElement, clearHistoryBtn);

  confirmClear = true;
  globalThis.document.activeElement = clearHistoryBtn;
  clearHistoryBtn.dispatchEvent({ type: "click" });
  assert.equal(clearHistoryBtn.disabled, true);
  assert.equal(emptyHistory.hidden, false);
  assert.equal(historyBody.childNodes.length, 0);
  assert.equal(globalThis.document.activeElement, historyTitle);

  globalThis.fetch = async (url) => {
    if (url === "/assets/viewer/demo-fixtures.json") {
      return {
        ok: true,
        headers: {
          get() {
            return "application/json";
          },
        },
        async json() {
          return {
            history: [{
              fileName: "board-pack-q3.pdf",
              status: "SUCCEEDED",
              submittedAt: "2026-07-03T00:10:00Z",
              jobId: "11111111-1111-4111-8111-111111111111",
              statusUrl: "/api/v1/convert/jobs/11111111-1111-4111-8111-111111111111",
            }],
            kpiSnapshot: {
              totalJobs: 1,
              succeededJobs: 1,
              conversionSuccessRate: 1,
              p95TimeToPreviewMs: 40,
            },
            kpiExports: [{
              exportedAt: "2026-07-03T00:20:00Z",
              subjectId: "buyer-demo-operator",
              totalJobs: 1,
            }],
          };
        },
      };
    }
    return {
      ok: false,
      headers: {
        get() {
          return "application/json";
        },
      },
      async json() {
        return null;
      },
    };
  };

  elements.get("load-demo-data-btn").dispatchEvent({ type: "click" });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(clearHistoryBtn.disabled, false);
  assert.equal(emptyHistory.hidden, true);
  assert.equal(historyBody.childNodes.length, 1);
  assert.equal(historyBody.childNodes[0].childNodes[0].textContent, "board-pack-q3.pdf");

  globalThis.document.activeElement = clearHistoryBtn;
  clearHistoryBtn.dispatchEvent({ type: "click" });
  assert.equal(clearHistoryBtn.disabled, true);
  assert.equal(emptyHistory.hidden, false);
  assert.equal(historyBody.childNodes.length, 0);
  assert.equal(globalThis.document.activeElement, historyTitle);

  elements.get("load-demo-data-btn").dispatchEvent({ type: "click" });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(clearHistoryBtn.disabled, false);
  assert.equal(emptyHistory.hidden, true);
  clearHistoryBtn.dispatchEvent({ type: "click" });
  assert.equal(clearHistoryBtn.disabled, true);
  assert.equal(emptyHistory.hidden, false);
});

test("an empty session keeps Clear history disabled until a row exists", async () => {
  const elements = new Map(elementIds.map(id => [id, new MockElement()]));

  globalThis.document = {
    activeElement: null,
    getElementById(id) {
      return elements.get(id);
    },
    createElement(tagName) {
      return new MockElement(tagName);
    },
  };
  globalThis.window = {
    confirm() {
      return true;
    },
    open() {
      return null;
    },
    setTimeout() {},
  };
  globalThis.localStorage = {
    getItem() {
      return "[]";
    },
    setItem() {},
  };
  globalThis.fetch = async () => ({
    ok: false,
    headers: {
      get() {
        return "application/json";
      },
    },
    async json() {
      return null;
    },
  });

  const moduleUrl = new URL(
    "../../main/resources/static/assets/viewer/demo.js",
    import.meta.url,
  );
  moduleUrl.searchParams.set("empty-history", String(Date.now()));
  await import(moduleUrl.href);
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(elements.get("clear-history-btn").disabled, true);
  assert.equal(elements.get("empty-history").hidden, false);
  assert.equal(elements.get("history-body").childNodes.length, 0);
});
});
