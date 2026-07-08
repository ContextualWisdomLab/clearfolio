## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.

## 2026-07-08 - 파일 업로드 시 경로 조작(Path Traversal) 취약점 방지
**Vulnerability:** 클라이언트에서 전송된 `MultipartFile.getOriginalFilename()`을 검증 없이 사용하고 있어 공격자가 `../../../etc/passwd.hwp` 같은 파일명으로 경로를 조작할 수 있었습니다.
**Learning:** 클라이언트가 전송한 파일명은 신뢰할 수 없는 입력값입니다. 경로 탐색 문자열이 포함될 수 있으며, 이를 그대로 사용할 경우 의도치 않은 디렉토리에 파일이 저장되거나 시스템 파일이 조작되는 등의 심각한 문제가 발생할 수 있습니다.
**Prevention:** 사용자로부터 입력받은 파일명은 항상 명시적으로 살균(sanitize)해야 합니다. `org.springframework.util.StringUtils.cleanPath()`를 사용하여 경로를 정규화하고, 마지막 `/` 이후의 순수한 파일명만 추출하여 사용하는 방식을 적용해야 합니다.
