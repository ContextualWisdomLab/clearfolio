import assert from "node:assert/strict";
import test from "node:test";

class MockTextNode {
  constructor(text) {
    this.type = "text";
    this.textContent = String(text);
  }
}

class MockElement {
  constructor(tagName = "div") {
    this.tagName = tagName.toUpperCase();
    this.attributes = new Map();
    this.childNodes = [];
    this.listeners = new Map();
    this.disabled = false;
    this.hidden = false;
    this.className = "";
    this.type = "";
    this.href = "";
    this.target = "";
    this.rel = "";
    this.files = [];
  }

  get textContent() {
    return this.childNodes.map(node => node.textContent).join("");
  }

  set textContent(value) {
    const text = String(value);
    this.childNodes = text === "" ? [] : [new MockTextNode(text)];
  }

  appendChild(node) {
    this.childNodes.push(node);
    return node;
  }

  append(...nodes) {
    this.childNodes.push(...nodes);
  }

  replaceChildren(...nodes) {
    this.childNodes = nodes;
  }

  addEventListener(type, listener) {
    this.listeners.set(type, listener);
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
  }

  getAttribute(name) {
    return this.attributes.has(name) ? this.attributes.get(name) : null;
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  focus() {}

  reset() {}
}

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

  globalThis.document = {
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
  globalThis.fetch = () => new Promise(resolve => {
    resolveStatusFetch = resolve;
  });

  statusButton.listeners.get("click")({ currentTarget: statusButton });

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
});
