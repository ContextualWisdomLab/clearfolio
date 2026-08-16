# Doctoring record: viewer refresh busy state and attempt identity

## Decision

The document viewer treats each refresh or polling sequence as one numbered attempt. Only the current attempt may change preview content, focus, live-region text, error presentation, or the refresh button's busy state.

The refresh button delegates temporary presentation changes to the shared `setBusyState` helper. The helper preserves the exact original child nodes, disables the button, and exposes `aria-busy="true"`; its returned restoration closure reverses those changes after the current attempt reaches a terminal state. Direct `textContent` replacement is prohibited because it can destroy nested icon and label structure.

A later refresh increments `currentAttemptId` before aborting the predecessor request. Every result-producing helper receives the attempt identity and fails closed when the value is stale. The current attempt therefore owns restoration even if an earlier network or rendering result arrives after the replacement attempt has started.

## Buyer impact

Users receive immediate progress feedback and cannot start duplicate refresh operations through the same control. Nested button content survives success and failure. A late predecessor response cannot replace a newer preview, move focus to a stale error, clear the current status, or prematurely restore the refresh control.

## Accessibility and interaction boundary

`aria-busy` describes the initiating refresh button while work is in progress; the preview region separately exposes its loading state. Error focus remains on the visible error heading, and ordinary successful completion updates the existing live status without moving focus.

This change does not claim that `aria-busy` alone guarantees an announcement across every assistive technology. It preserves the existing text status, focus, and error behavior and does not add a new send, mutation, tenant, artifact, or authorization capability.

## Verification contract

Repository tests and packaged-source contracts retain the signed artifact path and the attempt-aware inline PDF call. Exact-head CI must also prove the Java application, packaged WebJar assets, security checks, and fuzzing remain green.

A future rendered-browser acceptance lane should exercise two overlapping refreshes and prove that only the second attempt can publish preview content or restore the busy control. Until that lane exists, this record does not represent source inspection as browser-runtime proof.

## Compatibility and rollback

The public viewer URL, polling interval, status API, bootstrap API, signed artifact token, PDF.js version, and same-origin resource policy are unchanged. Rollback must revert the attempt identity and shared busy-state integration together; removing only the identity guard would reintroduce stale-result races, while removing only the restoration closure would strand the control in a busy state.

## References

World Wide Web Consortium. (2026). *Accessible Rich Internet Applications (WAI-ARIA) 1.3* (Working Draft). https://www.w3.org/TR/wai-aria-1.3/#aria-busy

World Wide Web Consortium. (2023, October 5). *Web Content Accessibility Guidelines (WCAG) 2.2* (W3C Recommendation). https://www.w3.org/TR/WCAG22/
