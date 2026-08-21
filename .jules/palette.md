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

## 2026-08-21 - 비동기 작업 중인 버튼의 마우스 커서 UX 개선
**Learning:** `aria-busy="true"`와 함께 버튼이 `disabled` 상태가 될 때, 기본 CSS의 `cursor: not-allowed`가 적용되면 사용자는 버튼이 유효하지 않은 상태라고 오해할 수 있습니다. 비동기 작업이 진행 중임을 명확히 하기 위해서는 `cursor: wait` 또는 `progress`를 사용하는 것이 훨씬 더 적절한 UX를 제공합니다.
**Action:** `aria-busy="true"`인 `disabled` 버튼에는 `cursor: not-allowed` 대신 `cursor: wait`가 적용되도록 CSS를 재정의하여 사용자에게 로딩 상태임을 명확히 인지시킵니다.
