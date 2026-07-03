## 2024-05-18 - Disabled and Loading States for Async Actions
**Learning:** Adding explicit loading and disabled states to asynchronous action buttons (like "Refresh") provides immediate feedback, reducing user confusion and preventing double-submissions.
**Action:** Always ensure that buttons triggering network requests visually indicate the loading state and are disabled until the request completes.

## 2026-07-03 - 동적 테이블의 컨텍스트를 포함한 ARIA 레이블 추가
**Learning:** 데이터 테이블 내의 일반적인 액션 버튼("상세" 또는 "상태" 등)은 다수의 행이 존재할 때 스크린 리더 사용자에게 심각한 접근성 문제를 야기합니다. 왜냐하면 어떤 항목에 적용되는 액션인지 컨텍스트를 잃기 때문입니다.
**Action:** 동적으로 테이블 행을 생성할 때, 액션 버튼과 링크의 `aria-label`에 컨텍스트(예: 행의 주된 엔티티 이름이나 ID)를 반드시 주입하여, 스크린 리더가 단순한 "상세" 대신 "document.pdf에 대한 상세 보기"로 읽어주도록 해야 합니다.
