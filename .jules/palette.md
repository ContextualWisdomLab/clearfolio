## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2024-07-08 - Dynamic Restoration of Button Text State
**Learning:** When temporarily modifying DOM element states (like updating button text to indicate loading), hardcoding the assumed original text for restoration is brittle and error-prone. Dynamically storing the element's initial `textContent` prevents mismatches if the text is changed or localized in the future.
**Action:** Always dynamically store the element's initial `textContent` in a local variable before modification and restore from that variable in a `finally` block.
