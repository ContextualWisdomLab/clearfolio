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

## 2026-09-26 - 필수 폼 필드의 접근성 패턴 개선
**Learning:** 필수 폼 필드에 시각적인 별표나 'required' 속성만 사용하는 대신, 레이블 내부에 명시적으로 '(required)' 텍스트를 제공하는 것이 스크린 리더 및 모든 사용자에게 더 명확한 접근성을 제공합니다. 또한 CSS Grid 컨테이너 내부의 인라인 요소는 의도치 않은 레이아웃 깨짐을 방지하기 위해 <span> 태그로 감싸야 합니다.
**Action:** 앞으로 폼 요소의 필수 여부를 나타낼 때는 접근성을 보장하기 위해 명시적인 텍스트를 사용하고, Grid 레이아웃의 자식 요소는 구조를 훼손하지 않도록 <span>으로 그룹화합니다.
