## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2026-07-01 - External Links and Structural ARIA
**Learning:** When using `aria-label` on structural elements (like `<div class="actions">`), they must have a valid role (e.g., `role="group"`) for screen readers to announce them. Also, links that open in a new tab (`target="_blank"`) must explicitly inform screen reader users via `aria-label` (e.g., 'Opens in a new tab') to prevent context loss, and should include `rel="noopener"`.
**Action:** Always verify that `aria-label` on non-interactive elements is accompanied by an appropriate role, and always add accessible context cues to external links.
