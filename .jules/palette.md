## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2026-07-04 - 비동기 작업 버튼의 로딩 상태 표시
**Learning:** 사용자는 비동기 작업(예: 네트워크 요청을 통한 데이터 새로고침)을 트리거하는 버튼을 클릭했을 때, 작업이 진행 중임을 명확하게 인지하지 못하면 여러 번 클릭하는 경향이 있습니다. 이는 중복 요청을 발생시키고 시스템에 불필요한 부하를 줄 수 있습니다.
**Action:** 비동기 네트워크 요청을 트리거하는 모든 버튼에는 클릭 시 즉각적으로 로딩 상태(예: 버튼 비활성화 및 텍스트 변경)를 반영하여 시각적 피드백을 제공하고 이중 제출을 방지해야 합니다. 작업이 완료되거나 실패하면 항상 원래 상태로 복구해야 합니다.
