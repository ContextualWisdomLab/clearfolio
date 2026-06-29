## 2026-06-26 - Vanilla JS Polling UX
**Learning:** Polling mechanisms in vanilla JS often miss crucial edge cases like temporarily disabling interactive elements (e.g., refresh buttons), leading to potential spam clicks and poor user experience.
**Action:** When auditing vanilla JS views, check for async operations like polling and verify interactive elements have proper `disabled` states and visual feedback.
