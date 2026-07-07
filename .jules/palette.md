## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.
## 2024-05-18 - External Link Context Switching UX
**Learning:** Opening secondary artifacts (like the JSON bootstrap or original file) in the same tab disrupts the user's viewing context and forces them to re-navigate. Adding `target="_blank"` with a descriptive `aria-label` ensures a smoother experience and maintains accessibility.
**Action:** Always configure external or secondary document links to open in a new tab (`target="_blank" rel="noopener noreferrer"`) and explicitly indicate this behavior to screen readers using `aria-label`.
