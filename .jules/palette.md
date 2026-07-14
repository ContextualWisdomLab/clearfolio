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

## 2026-07-14 - 동적 테이블 및 비동기 버튼 접근성 향상
**Learning:** 동적으로 생성되는 테이블 행 내의 아이콘/텍스트 버튼이나 링크(예: "Details", "Status JSON")는 스크린 리더 사용자에게 컨텍스트를 제공하지 못하는 경우가 많습니다. 또한, 비동기 작업 시 로딩 상태를 표시할 때 기존 노드를 단순히 삭제/재성성하는 대신 `innerHTML`을 백업/복원하면서 `aria-busy="true"` 속성을 부여하는 패턴이 이 프로젝트의 컴포넌트 접근성 및 상태 유지에 더 적합함을 확인했습니다.
**Action:** 앞으로 동적 테이블 액션 버튼을 생성할 때는 반드시 row의 고유 정보(예: 파일명)를 포함한 `aria-label`을 전달하고, 비동기 상태 토글 시에는 `innerHTML` 기반 복원과 `aria-busy` 속성을 기본 패턴으로 적용하겠습니다.
