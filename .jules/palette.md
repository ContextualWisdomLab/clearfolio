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
## 2024-11-20 - 비동기 작업 시 버튼의 로딩 상태 접근성 개선
**Learning:** 비동기 작업(API 호출 등)이 진행되는 동안 버튼이 비활성화될 때 `aria-busy="true"` 속성을 동적으로 설정해야 스크린 리더와 같은 보조 기술이 로딩 상태를 올바르게 인식할 수 있음을 확인했습니다. 단순히 `disabled` 속성만으로는 시각적 변화 외의 명확한 상태 변화를 전달하지 못합니다.
**Action:** 비동기 작업이 포함된 버튼 컴포넌트나 액션 요소를 구현할 때, 로딩이 시작되면 `disabled=true`와 함께 `aria-busy="true"`를 설정하고, 작업이 종료(finally)되면 `aria-busy` 속성을 제거하는 패턴을 기본적으로 적용합니다.
