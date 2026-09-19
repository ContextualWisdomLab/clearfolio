## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2024-07-10 - Async Button Loading States
**Learning:** Temporarily modifying the `innerHTML` of buttons for loading states requires saving and restoring the exact original `innerHTML` so nested DOM nodes (like icons or SVG paths) are not destroyed, rather than overwriting `textContent`.
**Action:** Superseded. For an async button whose interaction must be suppressed and whose child-node identity must be restored, use the product-owned `setBusyState` lease and release it on success, error, and cancellation. Do not serialize and recreate `innerHTML`.

## 2024-05-18 - 비동기 버튼 로딩 피드백 및 상태 복원
**Learning:** 비동기 작업 시 버튼에 명시적인 로딩 상태를 제공하면 사용자의 혼란을 줄이고 중복 요청을 방지할 수 있습니다.
**Action:** 이 규칙은 대체되었습니다. 상호작용을 막고 기존 child node identity를 복원해야 하는 비동기 버튼은 product-owned `setBusyState` lease를 사용하고 성공·오류·취소에서 해제합니다. `innerHTML` 직렬화·재생성은 사용하지 않습니다.

## 2026-07-13 - Async Table Actions UX
**Learning:** Adding explicit loading and disabled states to table action buttons that invoke asynchronous processes helps prevent redundant API calls and visually assures the user that their request is being handled.
**Action:** For async table actions that must suppress repeat activation and restore existing child nodes, acquire one `setBusyState` lease per operation and release it exactly once on success, error, or cancellation. Use a different documented pattern when native `disabled` would hide a focusable unavailable control or misrepresent progress semantics.

## 2026-09-17 - Button UX during polling
**Learning:** For long-running polling operations, manually setting the `textContent` of an asynchronous button destructively replaces its nested icon and DOM structure, breaking the design pattern upon restoration.
**Action:** Use `setBusyState` for an async button when repeat interaction must be suppressed and the original child-node objects must be restored after the operation. The busy view replaces those nodes temporarily; release the lease exactly once on success, error, or cancellation. Do not generalize this rule to controls that must remain focusable while unavailable.

## 2026-09-17 - 폴링 중 버튼 UX
**Learning:** 장기 폴링 작업 시 비동기 버튼의 `textContent`를 수동으로 설정하면 중첩된 아이콘 및 DOM 구조가 파괴되어 상태 복원 시 디자인 패턴이 깨질 수 있으며 접근성에 문제가 생깁니다.
**Action:** 반복 상호작용을 막고 원래 child node 객체를 작업 후 복원해야 하는 비동기 버튼에만 `setBusyState` lease를 사용합니다. Busy view에서는 기존 node가 일시적으로 교체되며, 성공·오류·취소에서 lease를 정확히 한 번 해제합니다. 사용할 수 없는 동안에도 focusable해야 하는 control에는 별도 문서화된 패턴을 사용합니다.
