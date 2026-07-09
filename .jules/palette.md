## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.
## 2026-07-09 - 버튼 내 로딩 텍스트
**Learning:** 비동기 작업 시 버튼 HTML을 임시로 로딩 텍스트로 교체하여 진행 상태를 시각적으로 보여주는 것은 사용자 경험(UX)을 크게 향상시킵니다.
**Action:** 비동기 작업을 시작하기 전에 버튼의 원래 innerHTML을 저장하고, 진행 상태를 나타내도록 HTML을 업데이트한 뒤, finally 블록에서 원래 HTML로 복원합니다.
