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

## 2026-09-17 - Button UX during polling
**Learning:** For long-running polling operations, manually setting the `textContent` of an asynchronous button destructively replaces its nested icon and DOM structure, breaking the design pattern upon restoration.
**Action:** Always use the robust `setBusyState` utility from `dom-utils.js` instead of manually setting `button.textContent` and `disabled`, as it preserves nested elements (icons, etc) securely, handles nested calls via depth counters, and accurately manages screen-reader ARIA states during the refresh polling cycle.

## 2026-09-17 - 폴링 중 버튼 UX
**Learning:** 장기 폴링 작업 시 비동기 버튼의 `textContent`를 수동으로 설정하면 중첩된 아이콘 및 DOM 구조가 파괴되어 상태 복원 시 디자인 패턴이 깨질 수 있으며 접근성에 문제가 생깁니다.
**Action:** `button.textContent` 및 `disabled`를 수동으로 설정하는 대신 `dom-utils.js`의 견고한 `setBusyState` 유틸리티를 항상 사용하여 중첩 요소(아이콘 등)를 안전하게 보존하고, 깊이 카운터를 통해 중첩 호출을 처리하며, 새로고침 폴링 주기 동안 스크린 리더 ARIA 상태를 정확하게 관리합니다.
