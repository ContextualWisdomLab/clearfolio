
const busyStates = new WeakMap();

/**
 * Sets an element to an idempotent busy state backed by a WeakMap depth counter.
 * Returns an idempotent restore function.
 *
 * @param {HTMLElement} btn - The element to set as busy.
 * @param {string} loadingText - The text to display while busy.
 * @returns {function} A function to restore the element to its original state.
 */
export function setBusyState(btn, loadingText) {
  let state = busyStates.get(btn);

  if (!state) {
    state = {
      depth: 0,
      originalNodes: Array.from(btn.childNodes || []),
      originalDisabled: btn.disabled,
      originalAriaBusy: btn.getAttribute("aria-busy"),
      originalAriaLabel: btn.getAttribute("aria-label")
    };
    busyStates.set(btn, state);

    btn.disabled = true;
    btn.textContent = loadingText;
    btn.setAttribute("aria-busy", "true");

    // Optionally set operation-specific accessible name if needed, but textContent handles it mostly.
    // If the element had an aria-label, we might want to update it to the loading text to be safe
    // but the instructions say "preserve original ... aria-label" which we do in the state.
    if (state.originalAriaLabel) {
      btn.setAttribute("aria-label", loadingText + " " + state.originalAriaLabel);
    }
  }

  state.depth++;

  let restored = false;

  return function restore() {
    if (restored) return;
    restored = true;

    const currentState = busyStates.get(btn);
    if (!currentState) return; // Should not happen

    currentState.depth--;

    if (currentState.depth === 0) {
      busyStates.delete(btn);

      btn.removeAttribute("aria-busy");
      if (currentState.originalAriaBusy !== null) {
        btn.setAttribute("aria-busy", currentState.originalAriaBusy);
      }

      if (currentState.originalAriaLabel !== null) {
        btn.setAttribute("aria-label", currentState.originalAriaLabel);
      } else {
        btn.removeAttribute("aria-label");
      }

      if (typeof btn.replaceChildren === 'function') {
        btn.replaceChildren(...currentState.originalNodes);
      } else {
        btn.textContent = "";
        currentState.originalNodes.forEach(node => btn.appendChild(node));
      }

      btn.disabled = currentState.originalDisabled;
    }
  };
}

export function createLink(href, label, ariaLabel) {
  const link = document.createElement("a");
  link.href = href;
  link.textContent = label;
  link.className = "table-link";
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  if (ariaLabel) {
    link.setAttribute("aria-label", ariaLabel);
  }
  return link;
}

export function createActionButton(label, onClick, ariaLabel) {
  const button = document.createElement("button");
  button.type = "button";
  button.textContent = label;
  button.className = "btn btn-secondary btn-compact";
  if (ariaLabel) {
    button.setAttribute("aria-label", ariaLabel);
  }
  button.addEventListener("click", onClick);
  return button;
}
