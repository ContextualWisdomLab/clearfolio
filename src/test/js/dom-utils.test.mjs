import assert from "node:assert/strict";
import test from "node:test";

import {
  createActionButton,
  createLink,
  setBusyState
} from "../../main/resources/static/assets/viewer/dom-utils.js";
import { MockElement, MockTextNode, MockDocumentFragment } from "./mock-dom.mjs";

globalThis.document = {
  createElement(tagName) {
    return new MockElement(tagName);
  },
  createDocumentFragment() {
    return new MockDocumentFragment();
  }
};

test("setBusyState restores an enabled control and the original node identities", () => {
  const button = new MockElement("button");
  const icon = new MockTextNode("★");
  const label = new MockTextNode("Submit document");
  button.replaceChildren(icon, label);

  const restore = setBusyState(button, "Submitting...");

  assert.equal(button.disabled, true);
  assert.equal(button.textContent, "Submitting...");
  assert.equal(button.getAttribute("aria-busy"), "true");
  assert.equal(button.getAttribute("aria-label"), "Submitting...");

  restore();

  assert.equal(button.disabled, false);
  assert.equal(button.getAttribute("aria-busy"), null);
  assert.equal(button.getAttribute("aria-label"), null);
  assert.deepEqual(button.childNodes, [icon, label]);
  assert.equal(button.childNodes[0], icon);
  assert.equal(button.childNodes[1], label);
});

test("setBusyState restores initially disabled and pre-labelled controls exactly", () => {
  const button = new MockElement("button");
  button.disabled = true;
  button.textContent = "Refresh evidence";
  button.setAttribute("aria-busy", "false");
  button.setAttribute("aria-label", "Refresh KPI evidence");

  const restore = setBusyState(button, "Refreshing...");

  assert.equal(button.disabled, true);
  assert.equal(button.getAttribute("aria-busy"), "true");
  assert.equal(button.getAttribute("aria-label"), "Refreshing... Refresh KPI evidence");

  restore();

  assert.equal(button.disabled, true);
  assert.equal(button.textContent, "Refresh evidence");
  assert.equal(button.getAttribute("aria-busy"), "false");
  assert.equal(button.getAttribute("aria-label"), "Refresh KPI evidence");
});

test("setBusyState gives an empty original accessible name a useful loading name", () => {
  const button = new MockElement("button");
  button.textContent = "Retry";
  button.setAttribute("aria-label", "");

  const restore = setBusyState(button, "Retrying...");

  assert.equal(button.getAttribute("aria-label"), "Retrying...");
  restore();
  assert.equal(button.getAttribute("aria-label"), "");
});

test("setBusyState waits for nested callers and makes every restore idempotent", () => {
  const button = new MockElement("button");
  button.textContent = "Details";
  button.setAttribute("aria-label", "View details for report.pdf");

  const restoreFirst = setBusyState(button, "Loading...");
  const restoreSecond = setBusyState(button, "Loading again...");

  assert.equal(button.textContent, "Loading...");
  assert.equal(button.getAttribute("aria-label"), "Loading... View details for report.pdf");

  restoreFirst();
  restoreFirst();
  assert.equal(button.disabled, true);
  assert.equal(button.getAttribute("aria-busy"), "true");

  restoreSecond();
  restoreSecond();
  assert.equal(button.disabled, false);
  assert.equal(button.textContent, "Details");
  assert.equal(button.getAttribute("aria-busy"), null);
  assert.equal(button.getAttribute("aria-label"), "View details for report.pdf");
});

test("createActionButton supports contextual and omitted accessible names", () => {
  let clicks = 0;
  const labelled = createActionButton(
    "Details",
    () => {
      clicks += 1;
    },
    "View details for report.pdf"
  );
  const unlabelled = createActionButton("Retry", () => {});

  assert.equal(labelled.tagName, "BUTTON");
  assert.equal(labelled.type, "button");
  assert.equal(labelled.textContent, "Details");
  assert.equal(labelled.className, "btn btn-secondary btn-compact");
  assert.equal(labelled.getAttribute("aria-label"), "View details for report.pdf");
  labelled.dispatchEvent({ type: "click" });
  assert.equal(clicks, 1);
  assert.equal(unlabelled.getAttribute("aria-label"), null);
});

test("createLink applies safe new-tab defaults and optional accessible names", () => {
  const labelled = createLink(
    "/viewer/document_id",
    "Open viewer",
    "Open viewer for report.pdf"
  );
  const unlabelled = createLink("/viewer/other_document", "Open viewer");

  assert.equal(labelled.tagName, "A");
  assert.equal(labelled.href, "/viewer/document_id");
  assert.equal(labelled.textContent, "Open viewer");
  assert.equal(labelled.className, "table-link");
  assert.equal(labelled.target, "_blank");
  assert.equal(labelled.rel, "noopener noreferrer");
  assert.equal(labelled.getAttribute("aria-label"), "Open viewer for report.pdf");
  assert.equal(unlabelled.getAttribute("aria-label"), null);
});

test("markup-like labels remain inert text in buttons and links", () => {
  const markupLabel = "<strong>Quarterly report</strong>";
  const button = createActionButton(
    markupLabel,
    () => {},
    `Details for ${markupLabel}`
  );
  const link = createLink(
    "/viewer/document_id",
    markupLabel,
    `Open ${markupLabel}`
  );

  assert.equal(button.textContent, markupLabel);
  assert.equal(button.childNodes.length, 1);
  assert.equal(button.childNodes[0].type, "text");
  assert.equal(link.textContent, markupLabel);
  assert.equal(link.childNodes.length, 1);
  assert.equal(link.childNodes[0].type, "text");
});
