## 2026-06-29 - [Global Security Headers]
**Vulnerability:** X-Content-Type-Options: nosniff 및 Strict-Transport-Security (HSTS) 헤더가 /viewer 경로에만 적용되어 API 엔드포인트(/api/v1/...)가 XSS 및 MITM 공격에 취약했습니다.
**Learning:** 애플리케이션의 특정 UI 경로(/viewer)를 위한 필터에 전역적으로 적용되어야 할 기본 보안 헤더들이 포함되어 있어 발생한 취약점이었습니다.
**Prevention:** 모든 애플리케이션 응답에 기본적으로 전역 보안 필터(Global Security Headers)를 적용하고, 특정 경로에만 필요한 규칙(예: CSP, Cache-Control)을 선택적으로 적용하는 방식을 사용해야 합니다.
