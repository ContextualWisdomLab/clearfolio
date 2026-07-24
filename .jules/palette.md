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
## 2026-07-24 - 동적 테이블 내 반복되는 액션 버튼에 컨텍스트를 제공하여 스크린 리더 접근성 개선
**Learning:** 데이터 테이블 내에서 '세부 정보'나 'JSON 상태'와 같이 동일한 텍스트를 가진 액션 버튼이 반복될 때, 스크린 리더 사용자는 어떤 항목에 대한 액션인지 맥락을 잃기 쉽습니다. 단순히 시각적인 텍스트뿐만 아니라, `aria-label`을 동적으로 생성(예: "Details for [파일명]")하여 명확한 맥락을 제공해야 합니다.
**Action:** DOM 요소를 동적으로 생성하는 헬퍼 함수(예: `createLink`, `createActionButton`) 작성 시 처음부터 선택적 인수로 `ariaLabel`을 받을 수 있도록 설계하고, 이를 통해 컨텍스트 인지형(context-aware) 라벨링을 기본 패턴으로 정착시켜야 합니다.
