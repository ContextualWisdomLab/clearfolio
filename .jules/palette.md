## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.
## 2026-07-08 - Dynamic Button Text Restoration
**Learning:** Hardcoding assumed original text when modifying button states can lead to mismatches if the text was changed elsewhere or localized.
**Action:** Dynamically store the element's initial `textContent` in a local variable before modification and restore from that variable in a `finally` block.
