## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.
## 2024-07-10 - 일관된 비동기 버튼 피드백
**Learning:** 네트워크 요청을 트리거하는 비동기 작업 버튼('새로고침' 또는 '제출' 등)에 로딩 상태 동안 명시적인 텍스트 피드백이 없어서 사용자가 작업이 처리 중인지 알기 어려웠습니다.
**Action:** 비동기 요청을 시작할 때 `disabled` 속성을 `true`로 설정하는 것과 함께 항상 버튼의 텍스트를 업데이트(예: "Loading...", "Submitting...")하고, 완료되면 원래 텍스트로 복원합니다.
