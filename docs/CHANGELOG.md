# 변경 사항

## [2026-06-25] 공통 보안 헤더(Global Security Headers) 전역 적용
- 모든 API 응답(Viewer 페이지 포함)에 `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-XSS-Protection`, `Strict-Transport-Security` 등의 필수 보안 헤더 적용
- API 응답(JSON 등)이 iframe에 삽입되는 것을 방지하기 위해 `X-Frame-Options: DENY` 적용
- API 응답에 대해 제한적인 Content-Security-Policy(`default-src 'none'`) 적용
- 보안 관련 검증 내역을 `ViewerSecurityHeadersWebFilterTest` 테스트 코드에 반영하여 100% 테스트 커버리지 유지
