## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2026-07-03 - Added missing disabled loading states to all interaction buttons
**Learning:** Found multiple buttons across the dashboard lacked proper loading/disabled feedback during async operations, a pattern specific to this app's implementation of Vanilla JS fetch patterns.
**Action:** Consistently update `el.button.textContent` inside `try...finally` blocks when toggling `disabled` to improve feedback for users when operations are processing.
