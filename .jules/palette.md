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

## 2024-08-17 - 비동기 버튼 로딩 시 명시적 aria-busy 속성 적용 및 상태 복원
**Learning:** 단독으로 작동하는 비동기 액션 버튼은 로딩 중일 때 시각적인 변화뿐만 아니라 스크린 리더 등 보조 기기에 정확한 상태를 전달하기 위해 동적으로 `aria-busy="true"` 속성을 부여해야 합니다. 또한, 로딩이 완료된 후 버튼 내부의 원래 DOM 구조를 안전하게 복원하기 위해 `innerHTML` 대신 `childNodes`를 저장해 두고 `replaceChildren`으로 복원하는 것이 권장됩니다.
**Action:** 비동기 네트워크 요청을 수행하는 버튼의 경우, 시작 시 `disabled` 설정과 함께 `aria-busy="true"`를 부여하고 원본 `childNodes`를 별도로 저장하여, 종료 시 상태 복원 및 `aria-busy` 속성을 제거(`removeAttribute`)하도록 일관되게 구현합니다.
