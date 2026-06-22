## 2026-06-22 - [Div 컨테이너의 ARIA Role 접근성]
**Learning:** `aria-label`은 시맨틱하지 않은 기본 `div` 요소에서는 스크린 리더에서 무시되는 경우가 많습니다.
**Action:** `div`와 같은 일반 컨테이너에 `aria-label`을 부여할 때는 반드시 적절한 `role` (예: `role="group"`)을 명시하여 스크린 리더가 레이블을 제대로 인식할 수 있게 해야 합니다.
