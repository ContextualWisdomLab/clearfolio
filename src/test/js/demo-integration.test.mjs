import assert from "node:assert/strict";
import test from "node:test";

import { MockElement } from "./mock-dom.mjs";

const SAME_ORIGIN_HTTPS = "https://demo.clearfolio.test";
const SAME_ORIGIN_HTTP = "http://demo.clearfolio.test";
const RELATIVE_STATUS_URL = "/api/v1/convert/jobs/document-identifier";
const CROSS_ORIGIN_STATUS_URL = "https://evil.example/api/v1/convert/jobs/document-identifier";

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

function createElements() {
  return new Map(elementIds.map(id => [id, new MockElement()]));
}

function createWindow(location) {
  const win = {
    confirm() {
      return true;
    },
    open() {
      return null;
    },
    setTimeout() {
      throw new Error("completed jobs must not schedule polling");
    },
  };
  if (location !== undefined) {
    win.location = location;
  }
  return win;
}

async function bootDemo({ location, statusUrl, fileName } = {}) {
  const elements = createElements();
  const resolvedFileName = fileName ?? "<img src=x onerror=alert(1)>.pdf";
  const history = [{
    fileName: resolvedFileName,
    status: "SUCCEEDED",
    submittedAt: "2026-08-05T00:00:00Z",
    jobId: "document-identifier",
    statusUrl: statusUrl ?? RELATIVE_STATUS_URL,
  }];

  globalThis.document = {
    getElementById(id) {
      return elements.get(id);
    },
    createElement(tagName) {
      return new MockElement(tagName);
    },
  };
  globalThis.window = createWindow(location);
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
  moduleUrl.searchParams.set("integration", `${Date.now()}-${Math.random()}`);
  await import(moduleUrl.href);
  await new Promise(resolve => setImmediate(resolve));
  return { elements, fileName: resolvedFileName };
}

function statusButtonOf(elements) {
  return elements.get("history-body").childNodes[0].childNodes[3].childNodes[1];
}

function installPopup() {
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
  return { popup, popupBody };
}

function installStatusFetchProbe() {
  const fetchedUrls = [];
  let resolveStatusFetch;
  globalThis.fetch = url => {
    fetchedUrls.push(url);
    return new Promise(resolve => {
      resolveStatusFetch = resolve;
    });
  };
  return {
    fetchedUrls,
    resolve(response) {
      resolveStatusFetch(response);
    },
  };
}

test("the executable demo renders inert actions and blocks repeated status activation", async () => {
  const { elements, fileName } = await bootDemo({
    location: new URL(`${SAME_ORIGIN_HTTPS}/`),
  });

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

  const statusButton = actionsCell.childNodes[1];
  const { popup, popupBody } = installPopup();
  const probe = installStatusFetchProbe();

  statusButton.dispatchEvent({ type: "click" });
  statusButton.dispatchEvent({ type: "click" });

  assert.equal(probe.fetchedUrls.length, 1);
  assert.equal(
    probe.fetchedUrls[0],
    `${SAME_ORIGIN_HTTPS}${RELATIVE_STATUS_URL}`,
  );
  assert.equal(statusButton.disabled, true);
  assert.equal(statusButton.textContent, "Loading status JSON...");
  assert.equal(statusButton.getAttribute("aria-busy"), "true");
  assert.equal(
    statusButton.getAttribute("aria-label"),
    `Loading status JSON... View status JSON for ${fileName}`,
  );
  assert.equal(popup.opener, null);
  assert.equal(popupBody.childNodes[0].textContent, "Loading...");

  probe.resolve({
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
});

test("same-origin demo status URLs are admitted when window.location is http or https", async () => {
  for (const origin of [SAME_ORIGIN_HTTPS, SAME_ORIGIN_HTTP]) {
    const { elements } = await bootDemo({
      location: { origin },
      statusUrl: RELATIVE_STATUS_URL,
      fileName: "report.pdf",
    });
    installPopup();
    const probe = installStatusFetchProbe();
    statusButtonOf(elements).dispatchEvent({ type: "click" });
    assert.equal(probe.fetchedUrls.length, 1);
    assert.equal(probe.fetchedUrls[0], `${origin}${RELATIVE_STATUS_URL}`);
    assert.match(probe.fetchedUrls[0], /^https?:\/\/demo\.clearfolio\.test\//);
  }
});

test("demo status URL resolution fail-closes without location or across origins", async () => {
  const cases = [
    {
      location: undefined,
      statusUrl: RELATIVE_STATUS_URL,
    },
    {
      location: { origin: SAME_ORIGIN_HTTPS },
      statusUrl: CROSS_ORIGIN_STATUS_URL,
    },
    {
      location: { origin: SAME_ORIGIN_HTTPS },
      statusUrl: `https://attacker:secret@demo.clearfolio.test${RELATIVE_STATUS_URL}`,
    },
  ];

  for (const scenario of cases) {
    const { elements } = await bootDemo({
      ...scenario,
      fileName: "report.pdf",
    });
    const probe = installStatusFetchProbe();
    statusButtonOf(elements).dispatchEvent({ type: "click" });
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(probe.fetchedUrls.length, 0);
    assert.equal(elements.get("demo-error-message").textContent, "Invalid status URL.");
  }
});
