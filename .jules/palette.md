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
## 2026-07-14 - 보조 비동기 액션을 위한 테이블 버튼 UX
**Learning:** 새로운 탭에서 JSON 페이로드를 실행하는 것과 같이 사이드 이펙트를 일으키는 인라인 테이블 비동기 액션 버튼은 클릭 시 즉각적인 로딩 피드백을 제공하고 추가 클릭을 방지하기 위해 잠금 처리되어야 합니다.
**Action:** 비동기 동작을 트리거하는 모든 인라인 액션 버튼에 로딩 상태(비활성화 및 "Loading..." 텍스트 변경)를 일관되게 적용하고, `.finally()` 블록에서 원래 DOM 상태로 올바르게 복원해야 합니다.
