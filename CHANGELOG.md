# 변경 사항

## [Unreleased] - 2026-06-26

### 변경 (Changed)
- 외부 링크(새 탭 열기) 사용자 경험(UX) 및 접근성(A11y) 개선
  - 문서 뷰어(`viewer.js`) 및 UI 컨트롤러(`ViewerUiController.java`)의 유틸리티 링크에 새 탭 열기(`target="_blank"`) 속성 추가
  - 시각 장애인 및 화면 판독기 사용자를 위한 `aria-label` 속성 추가 및 링크 그룹화를 위한 `role="group"` 속성 적용
  - 새 탭에서 열림을 나타내는 시각적 인디케이터(`↗`) 추가
