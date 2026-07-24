## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2024-07-10 - Async Button Loading States
**Learning:** Temporarily modifying the `innerHTML` of buttons for loading states requires saving and restoring the exact original `innerHTML` so nested DOM nodes (like icons or SVG paths) are not destroyed, rather than overwriting `textContent`.
**Action:** Always store the original `innerHTML` dynamically in a local variable before updating a button to a loading state, and restore it in the `finally` block to preserve nested structure.

## 2024-05-18 - 비동기 버튼 로딩 피드백 및 상태 복원
**Learning:** 비동기 작업 시 버튼에 명시적인 로딩 상태를 제공하면 사용자의 혼란을 줄이고 중복 요청을 방지할 수 있습니다.
**Action:** 비동기 버튼 텍스트 변경 시, `innerHTML`을 임시 변수에 저장하고 `finally` 블록에서 복원하여 내부 DOM 구조 손실 없이 상태 피드백을 제공해야 합니다.

## 2026-07-13 - Async Table Actions UX
**Learning:** Adding explicit loading and disabled states to table action buttons that invoke asynchronous processes helps prevent redundant API calls and visually assures the user that their request is being handled.
**Action:** Consistently apply `disabled` state and `Loading...` text changes to inline table action buttons linked to async workflows, and carefully preserve underlying DOM structures with `Array.from(btn.childNodes)` during the loading cycle to avoid rendering regressions.

## 2026-10-27 - Screen Reader Support for Repetitive Table Actions and Loading States
**Learning:** Repetitive table action buttons or links (e.g., 'Details', 'Open') must have dynamically generated, context-specific `aria-label` attributes that include row-specific identifiers (like the filename) to aid screen reader users. Additionally, when changing a button's state to "Loading...", it is crucial to temporarily update the `aria-label` to reflect this state and restore the original `aria-label` afterwards, because screen readers prioritize `aria-label` over text content. Failure to update it causes screen readers to miss critical loading feedback.
**Action:** Always provide row-specific identifiers in `aria-label` for repetitive table actions. When modifying an element to display a loading state, if it has an `aria-label`, temporarily update it to reflect the loading state (e.g., `setAttribute("aria-label", "Loading...")`) and restore the original label afterwards.
