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
## 2024-05-18 - 비동기 작업 중 버튼의 접근성 개선
**Learning:** 비동기 작업(예: 데이터 로드, 제출, 재시도)이 진행 중일 때, 단순히 버튼을 비활성화(disabled)하고 텍스트를 변경하는 것만으로는 화면 판독기 사용자에게 로딩 상태가 진행 중임을 명확하게 전달하기 어려울 수 있습니다. `aria-busy="true"` 속성을 동적으로 추가하면 화면 판독기가 요소가 현재 업데이트 중이거나 로딩 중임을 인식하고 사용자에게 적절히 안내할 수 있습니다.
**Action:** 비동기 액션 버튼에 로딩 상태를 표시할 때, 텍스트 변경이나 `disabled` 속성 추가와 함께 반드시 `aria-busy="true"`를 설정하고 작업이 완료(finally 블록)되면 `removeAttribute("aria-busy")`로 제거하는 패턴을 적용합니다.
