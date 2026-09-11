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

## 2026-09-11 - 시각적 필수 입력 표시 추가
**Learning:** HTML5의 required 속성을 사용하는 폼 필드에 시각적 필수 표시(*)를 추가할 때, 스크린 리더의 중복 읽기를 방지하기 위해 반드시 aria-hidden="true"를 적용해야 합니다. 또한, grid 레이아웃이 적용된 부모 컨테이너에서는 내부 요소가 분리되지 않도록 텍스트와 표시를 하나의 span으로 감싸야 합니다.
**Action:** 폼 필드에 시각적 필수 표시자를 추가할 때는 중복 접근성을 방지하기 위해 aria-hidden을 사용하고, 레이아웃 깨짐을 방지하기 위해 올바른 래핑 요소를 사용하세요.
