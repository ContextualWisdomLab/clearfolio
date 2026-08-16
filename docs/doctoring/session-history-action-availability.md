# Doctoring record: session-history clear action availability

## Decision

The buyer-demo session-history clear action is a native button whose availability follows the same history collection that is rendered in the table.

- The server-rendered empty state starts with the button disabled.
- The control exposes the contextual accessible name `Clear session history` rather than the ambiguous visible label `Clear` alone.
- Every `renderHistory` call synchronizes `disabled` with whether at least one clearable history record exists.
- Loading demo data, adding a session, clearing history, and re-rendering an empty state therefore cannot leave stale action availability.

## Buyer impact

Users no longer encounter an apparently executable destructive action when there is nothing to clear. Assistive-technology users receive the action's object as part of its accessible name, while sighted users retain the compact button label.

## Standards and design boundary

HTML defines the native `disabled` state as making a button unavailable for activation. WAI-ARIA's first rule of use prefers native host-language semantics when they provide the required behavior; this component therefore uses `disabled` rather than recreating an unavailable state through `aria-disabled` and custom event suppression.

This decision is specific to a permanently unavailable empty-state action. It does not establish that every temporarily unavailable action should be removed from the Tab sequence. Controls whose prerequisite explanation must remain discoverable may require a focusable `aria-disabled` pattern with visible explanatory text and an explicit inert action boundary.

The change does not claim whole-product WCAG conformance or browser/screen-reader interoperability. It does not alter the history storage format, retention policy, tenant authority, conversion jobs, signed links, or production workspace bootstrap.

## Verification contract

`ViewerUiControllerTest.homeReturnsBuyerDemoUploadShell` proves the initial document contains one disabled, contextually named control. Existing buyer-demo tests execute the shipped script, and `renderHistory` derives availability from the rendered history collection rather than from a separate mutable flag.

Exact-head repository CI, fuzzing, Security Scan, and Semgrep remain authoritative for integration. Predecessor-head results do not transfer after any commit.

## Rollback

Rollback must remove the initial disabled state and the render-time synchronization together. Reverting only one side would reintroduce stale availability either before hydration or after history updates. The contextual accessible name should not be removed merely to shorten the visible label.

## References

WHATWG. (2026). *HTML Living Standard: The button element*. https://html.spec.whatwg.org/multipage/form-elements.html#the-button-element

World Wide Web Consortium Web Accessibility Initiative. (2025). *First rule of ARIA use*. https://www.w3.org/TR/using-aria/#rule1

World Wide Web Consortium. (2023, October 5). *Web Content Accessibility Guidelines (WCAG) 2.2* (W3C Recommendation). https://www.w3.org/TR/WCAG22/
