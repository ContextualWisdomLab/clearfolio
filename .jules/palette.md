## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2026-07-06 - Async Button Loading States
**Learning:** Asynchronous action buttons (e.g., Refresh, Submit) in this app require explicit visual loading states (updating textContent and disabled=true) to prevent double-submissions and improve UX.
**Action:** Always implement disabled and text state changes on buttons triggering network requests, restoring them in a finally block.
