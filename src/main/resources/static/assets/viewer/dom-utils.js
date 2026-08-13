const busyStates = new WeakMap();

/**
 * Applies a nested-safe asynchronous busy state to a button.
 *
 * <p>The first caller snapshots the button's child nodes, disabled state, and
 * relevant ARIA attributes. Later callers only increment a depth counter.
 * Each returned callback is idempotent, and the original state is restored
 * only after every caller has released its busy state.</p>
 *
 * @param {HTMLButtonElement} button button that starts asynchronous work
 * @param {string} loadingText visible and accessible pending-state label
 * @returns {() => void} idempotent callback that releases one busy-state claim
 */
export function setBusyState(button, loadingText) {
  let state = busyStates.get(button);

  if (state === undefined) {
    state = {
      depth: 0,
      originalNodes: Array.from(button.childNodes),
      originalDisabled: button.disabled,
      originalAriaBusy: button.getAttribute("aria-busy"),
      originalAriaLabel: button.getAttribute("aria-label")
    };
    busyStates.set(button, state);

    button.disabled = true;
    button.textContent = loadingText;
    button.setAttribute("aria-busy", "true");
    button.setAttribute(
      "aria-label",
      state.originalAriaLabel === null || state.originalAriaLabel === ""
        ? loadingText
        : `${loadingText} ${state.originalAriaLabel}`
    );
  }

  state.depth += 1;
  let restored = false;

  return function restoreBusyState() {
    if (restored) {
      return;
    }
    restored = true;
    state.depth -= 1;

    if (state.depth !== 0) {
      return;
    }

    busyStates.delete(button);
    restoreNullableAttribute(button, "aria-busy", state.originalAriaBusy);
    restoreNullableAttribute(button, "aria-label", state.originalAriaLabel);
    button.replaceChildren(...state.originalNodes);
    button.disabled = state.originalDisabled;
  };
}

/**
 * Creates a new-tab link with an optional contextual accessible name.
 *
 * @param {string} href destination URL
 * @param {string} label visible link text
 * @param {string | undefined} ariaLabel contextual accessible name
 * @returns {HTMLAnchorElement} configured link element
 */
export function createLink(href, label, ariaLabel) {
  const link = document.createElement("a");
  link.href = href;
  link.textContent = label;
  link.className = "table-link";
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  if (ariaLabel !== undefined) {
    link.setAttribute("aria-label", ariaLabel);
  }
  return link;
}

/**
 * Creates a compact action button with an optional contextual accessible name.
 *
 * @param {string} label visible button text
 * @param {(event: Event) => void} onClick click handler
 * @param {string | undefined} ariaLabel contextual accessible name
 * @returns {HTMLButtonElement} configured action button
 */
export function createActionButton(label, onClick, ariaLabel) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = "btn btn-secondary btn-compact";
  if (ariaLabel !== undefined) {
    button.setAttribute("aria-label", ariaLabel);
  }
  button.addEventListener("click", onClick);
  return button;
}

/**
 * Restores an attribute exactly, distinguishing absence from an empty value.
 *
 * @param {HTMLElement} element element whose attribute is restored
 * @param {string} attributeName attribute to restore
 * @param {string | null} originalValue original value or null when absent
 * @returns {void}
 */
function restoreNullableAttribute(element, attributeName, originalValue) {
  if (originalValue === null) {
    element.removeAttribute(attributeName);
  } else {
    element.setAttribute(attributeName, originalValue);
  }
}
