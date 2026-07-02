## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2024-05-18 - Accessibility of New Tab Links and Action Groups
**Learning:** Screen readers require explicit context when links open in new tabs, and `aria-label` on generic structural elements like `<div>` only works when combined with a valid landmark or component `role` (like `role="group"`).
**Action:** Always add `target="_blank"`, `rel="noopener noreferrer"`, and an explicit `aria-label` stating "in a new tab" for external links. Always pair `aria-label` on containers with appropriate roles like `role="group"` to ensure they are announced.
