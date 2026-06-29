# 변경 사항 (Changelog)

## 2026-06-28
- **UI/UX 개선**: `ViewerUiController.java`에서 불필요한 중복 `aria-label` 제거 및 `role="group"`, `aria-describedby` 속성을 추가하여 접근성을 향상시켰습니다.
- **UI/UX 개선**: `ViewerUiController.java`와 `viewer.js`에서 외부 링크 및 생성된 아티팩트 링크 클릭 시 새 탭에서 안전하게 열리도록 `target="_blank"`와 `rel="noopener noreferrer"` 속성을 추가하였습니다.
- **UI/UX 개선**: 문서 미리보기가 로드될 때 빈 상태 안내 문구(`.help`)가 화면에 남지 않도록 `viewer.js`의 `clearPreview()` 로직을 수정했습니다.
