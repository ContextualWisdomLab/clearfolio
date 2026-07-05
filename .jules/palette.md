## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.
## 2026-07-05 - Loading states for Refresh Evidence Button
**Learning:** Added asynchronous loading state handling for refresh buttons to provide immediate user feedback and prevent double submission.
**Action:** Applied 'try-finally' block pattern to safely toggle button disabled and textContent properties around async operations.
