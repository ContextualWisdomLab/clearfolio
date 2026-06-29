# UX and Accessibility Journal

## 2026-06-27 - [Refresh Button Disabled State & Link Accessibility]
**Learning:** Polling mechanisms without visual disabled states led to potential duplicate clicking. Externally opening links dynamically created via JS were lacking accessibility metadata.
**Action:** Implemented dynamic disabling in `viewer.js`, visual disabled feedback in `viewer.css` using `:disabled` and `:not(:disabled)` pseudo-classes, and added `target="_blank" rel="noopener noreferrer"` to external links. Also assigned `role="group"` to action clusters.
