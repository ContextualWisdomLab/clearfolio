## 2026-06-29 - [Global Security Headers]
**Vulnerability:** 누락된 글로벌 보안 헤더(HSTS)
**Learning:** `ViewerSecurityHeadersWebFilter`에서 CSP는 잘 설정되었으나 글로벌 애플리케이션 보안 수준을 강화하기 위한 HTTP Strict Transport Security(HSTS)가 빠져 있었습니다.
**Prevention:** 모든 관련 필터나 애플리케이션 설정에서 기본적으로 브라우저 보안 헤더가 올바르게 적용되도록 구성합니다.
