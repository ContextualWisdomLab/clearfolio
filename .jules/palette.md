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

## 2026-08-13 - 빈 상태일 때의 동작 제한 버튼의 UX 개선
**Learning:** 데이터가 비어 있는 상태(Empty State)에서 실행 불가능한 동작(예: 히스토리 지우기)의 버튼을 활성화된 채로 두면 사용자에게 혼란을 줄 수 있으며, 아이콘이 없는 텍스트 버튼이더라도 'Clear'와 같이 문맥상 목적이 모호한 텍스트를 사용할 경우 스크린 리더 사용자에게 명확한 의미가 전달되지 않습니다.
**Action:** 동작을 수행할 데이터가 없을 때는 해당 버튼에 `disabled` 속성을 부여하여 시각적/기능적으로 비활성화하고, 문맥이 필요한 버튼에는 `aria-label`을 추가하여 접근성을 높이는 패턴을 모든 빈 상태(Empty State) 디자인에 일관되게 적용합니다.
