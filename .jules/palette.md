## 2026-09-30 - Add explicit text (required) to field label
**Learning:** Required form fields need explicit text `(required)` inside the label for accessibility, rather than using styled asterisks. When doing so inside a CSS grid container like `.field-label`, the text needs to be wrapped in a `<span>` to prevent `display: grid` from forcing elements onto separate tracks.
**Action:** Use explicit `(required)` text wrapped in `<span>` for required fields in CSS grids.
