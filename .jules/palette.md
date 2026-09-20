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
## 2026-09-20 - 폼 레이블 필수 표시자 추가 및 버튼 접근성 개선 (Adding ARIA attributes to visual indicators and buttons)
**Learning:** `required` 속성이 이미 적용된 `input` 요소의 레이블에 추가된 시각적 표시자(`*`)에 `aria-hidden="true"`를 추가하여 스크린 리더가 불필요하게 반복해서 읽지 않도록 방지했습니다. 접근성 지침 WCAG 2.5.3 (Label in Name)을 준수하기 위해 컨텍스트가 부족한 데모 버튼에 원래의 텍스트가 연속된 문자열로 포함되도록 `aria-label`을 수정했습니다.
**Action:** HTML5 `required` 속성을 사용하는 폼 필드에 시각적 표시자(`*`)를 추가할 때는 항상 `aria-hidden="true"`를 적용해야 합니다. 또한, 텍스트가 있는 버튼에 `aria-label`을 적용할 때는 반드시 버튼의 시각적인 텍스트가 연속된 부분 문자열로 포함되도록 작성하여 음성 제어 사용자가 접근할 수 있도록 해야 합니다.
