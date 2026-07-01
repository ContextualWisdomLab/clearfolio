## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.
## 2024-05-18 - Loading Indicators for Async Refresh Actions
**Learning:** Text changes (like "Refresh" to "Refreshing...") are often not immediately perceived by users. Adding an explicit, animated loading spinner next to the text provides a universally recognized visual cue that an asynchronous process is underway, reducing impatience and uncertainty.
**Action:** Always include a visual loading indicator (like a spinner) alongside text changes on buttons that trigger network requests, ensuring it is accessible (`aria-hidden="true"` for decorative spinners) and respects reduced motion preferences.
