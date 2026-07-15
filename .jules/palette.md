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
## 2024-05-24 - 동적 테이블 및 비동기 작업의 접근성 향상
**Learning:** 동적으로 생성되는 테이블 내 액션 버튼(예: Details, Status JSON)은 텍스트만으로 스크린 리더에서 맥락을 파악하기 어려우므로 동적 `aria-label`(예: `Details for [파일명]`)을 부여해야 혼동을 방지할 수 있음을 확인했습니다. 또한, 비동기 작업 시 버튼이 단순히 비활성화(disabled)되는 것 외에도 `aria-busy="true"`를 적용해야 보조 기술이 로딩 상태를 명확히 인지함을 재확인했습니다.
**Action:** 향후 리스트나 테이블 안에서 반복되는 액션 요소나 비동기 요청을 수행하는 요소 구현 시, 동적인 식별 정보를 포함한 `aria-label`과 `aria-busy` 상태 토글을 필수로 적용하겠습니다.
