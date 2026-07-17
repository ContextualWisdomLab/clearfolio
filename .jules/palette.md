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

## 2026-07-17 - 비동기 작업 버튼에 대한 aria-busy 접근성 상태 적용
**Learning:** 비동기 작업(API 호출, 로딩 상태 등)이 실행되는 동안 버튼에 단순히 `disabled` 처리나 텍스트 변경만으로는 스크린 리더 등 보조 기기에 현재 작업이 진행 중이라는 상태가 명확히 전달되지 않을 수 있습니다.
**Action:** `demo.js` 내의 모든 비동기 작업 버튼(로딩 중 상태를 갖는 버튼들)에 대해, 작업 시작 시 동적으로 `aria-busy="true"` 속성을 부여하고, 작업이 끝나는 `finally` 블록에서 해당 속성을 제거(`removeAttribute("aria-busy")`)하도록 수정하여 접근성 상태(로딩 상태)를 명확하게 알릴 수 있도록 적용했습니다.
