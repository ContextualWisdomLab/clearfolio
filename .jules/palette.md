## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2026-07-07 - 비동기 작업 버튼에 명시적인 로딩 상태 필요
**학습내용:** `demo.js`의 데모 UI 내 비동기 작업 버튼들이 처리 과정 중 시각적 피드백을 제공하지 못하는 접근성/UX 문제를 발견했습니다. 이러한 피드백 부재는 사용자의 혼란을 초래하고 중복 제출을 허용하여 전반적인 애플리케이션 경험을 저하시킬 수 있습니다.
**적용방법:** 모든 비동기 작업 버튼이 실행되는 동안 버튼의 텍스트(예: "Submitting...", "Retrying...", "Refreshing...")를 명시적으로 변경하고 `disabled` 속성을 true로 설정하여 로딩 상태임을 명확히 나타내도록 합니다. 오류 발생 시 사용자가 작동 불능 상태에 빠지지 않도록 `finally` 블록에서 기존 상태로 복원합니다.
## 2026-07-07 - Test Coverage Issue with UX Adjustments
**Learning:** Found that frontend JS changes could be made without updating tests and 100% test coverage instruction was explicitly provided but the project only has backend tests in JUnit/Mockito, meaning javascript changes do not show up in the jacoco test coverage metric. The JS changes don't have unit tests to augment.
**Action:** When working on UX issues inside vanilla Javascript files, verify frontend logic manually via e2e checks and UI verifications, and note that there are no javascript unit tests to fulfill the 100% coverage requirement.
