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
## 2026-07-02 - 동적 테이블의 동작 버튼 및 링크 접근성 개선
**Learning:** 동적으로 생성되는 테이블 행의 동작 버튼(예: 'Details', 'Status JSON')이나 링크가 시각적으로는 맥락을 알 수 있으나, 스크린 리더 사용자에게는 어떤 항목에 대한 동작인지 명확하게 전달되지 않는 문제가 발견되었습니다.
**Action:** `demo.js`의 DOM 요소를 동적으로 생성하는 함수(`createLink`, `createActionButton`)에 `ariaLabel` 매개변수를 추가하고, 각 행을 렌더링할 때 파일명을 포함한 구체적인 문맥(예: `Details for board-pack-q3.pdf`)을 `aria-label` 속성으로 부여하여 접근성을 향상시켜야 합니다.
