## 2026-06-29 - `javascript:` URI XSS 방지
**Vulnerability:** `renderPreviewLink` 함수 내 `javascript:` URI를 통한 XSS.
**Learning:** `innerHTML`을 사용하지 않더라도, 검증되지 않은 경로를 직접 `a.href = untrusted_path`에 할당하면 사용자가 해당 링크를 클릭했을 때 악의적인 스크립트(`javascript:` 또는 `data:` 프로토콜)가 실행되는 Cross-Site Scripting (XSS) 취약점이 발생할 수 있습니다.
**Prevention:** 프론트엔드 코드에서는 URL을 DOM 속성(`href` 또는 `src`)에 할당하기 전, `URL` 생성자 등을 사용하여 `http:`나 `https:` 같은 안전한 프로토콜인지 항상 검증하고 처리해야 합니다.
