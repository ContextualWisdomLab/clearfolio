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
## 2026-07-02 - 비동기 버튼 상태 변경 시 원본 DOM 구조 보존 및 aria-busy 속성 활용
**Learning:** 비동기 작업 중 버튼의 텍스트를 하드코딩된 문자열(예: "Refresh")로 단순히 변경하면 버튼 내부의 기존 자식 요소(SVG 아이콘 등)나 상태가 손실되며, 시각적 일관성과 웹 접근성이 훼손될 수 있습니다. 또한 진행 중인 상태를 스크린 리더 등에 올바르게 전달하려면 단순한 `disabled` 처리 외에도 `aria-busy="true"` 속성이 필수적입니다.
**Action:** 비동기 상태를 처리할 때는 항상 `dom-utils.js`의 `setBusyState`와 같은 유틸리티를 사용하여 진입 시점의 원본 DOM 상태(child node 및 속성)를 동적으로 스냅샷으로 저장하고 복구해야 합니다. 또한 로딩 중에는 반드시 `aria-busy="true"`를 활성화하여 보조 기기에서 상태 변화를 감지할 수 있도록 일관된 패턴을 적용해야 합니다.
