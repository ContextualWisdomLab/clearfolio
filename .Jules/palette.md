## 2026-06-27 - No Custom CSS Classes Allowed for UI State
**Learning:** When making UX improvements (like disabled button states), adding new custom classes (e.g., `.btn-disabled`) violates the design constraints. Native pseudo-classes (`:disabled`) and combining selectors (`:not(:disabled)`) must be used instead to leverage standard browser behavior without expanding the design system.
**Action:** Use native HTML boolean state attributes (like `disabled`) and corresponding CSS pseudo-classes to manage visual state instead of creating new utility classes.
