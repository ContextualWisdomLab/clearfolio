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
## 2026-08-07 - 동적 로딩 상태에서 aria-busy 속성의 중요성
**Learning:** 비동기 작업(예: 제출, 데이터 로드) 중에 버튼 텍스트를 "로딩 중..."과 같이 명시적으로 업데이트하더라도, 스크린 리더는 이 시각적인 또는 텍스트 변화를 즉각적으로 사용자에게 전달하지 않을 수 있습니다.
**Action:** 로딩 상태를 표시하는 요소에는 동적으로 `aria-busy="true"` 속성을 추가하여 스크린 리더 등 보조 기기가 요소가 현재 처리 중임을 정확하게 인식하도록 해야 합니다. 작업이 끝나면 `finally` 블록에서 해당 속성을 제거하여 상태를 초기화해야 합니다.
