## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.

## 2026-07-08 - 파일 업로드 시 경로 조작(Path Traversal) 취약점 방지
**Vulnerability:** 클라이언트에서 전송된 `MultipartFile.getOriginalFilename()`을 검증 없이 사용하고 있어 공격자가 `../../../etc/passwd.hwp` 같은 파일명으로 경로를 조작할 수 있었습니다.
**Learning:** 클라이언트가 전송한 파일명은 신뢰할 수 없는 입력값입니다. 경로 탐색 문자열이 포함될 수 있으며, 이를 그대로 사용할 경우 의도치 않은 디렉토리에 파일이 저장되거나 시스템 파일이 조작되는 등의 심각한 문제가 발생할 수 있습니다.
**Prevention:** 사용자로부터 입력받은 파일명은 항상 명시적으로 살균(sanitize)해야 합니다. `org.springframework.util.StringUtils.cleanPath()`를 사용하여 경로를 정규화하고, 마지막 `/` 이후의 순수한 파일명만 추출하여 사용하는 방식을 적용해야 합니다.

## 2026-07-02 - Cryptographic Signature Verification Bypass in Policy Override
**Vulnerability:** The document validation service logged the presence of policy override parameters (approverId, approvalToken) but failed to actually verify the cryptographic signature of the token against a shared secret. This allowed an attacker to bypass file extension restrictions (e.g., uploading blocked `.hwp` files) by sending any arbitrary token.
**Learning:** Checking for the presence of security tokens is insufficient if the token payload and signature are not cryptographically validated against a trusted secret. The absence of this check created a critical authorization bypass.
**Prevention:** Always verify cryptographic signatures (using constant-time comparison like `MessageDigest.isEqual`) for any policy override or authorization token before granting the elevated privilege or bypassing a security control.

## 2026-07-08 - Length Extension and Canonicalization Vulnerability in Hash Payloads
**Vulnerability:** The HMAC-SHA256 signature payload for policy overrides was constructed by simply concatenating strings: `approverId + ":" + extension`. This allowed attackers to craft ambiguous inputs if they embedded the delimiter `:` inside their payload, potentially bypassing validation via canonicalization or length extension attacks.
**Learning:** Simple string concatenation is insecure when generating cryptographic hashes or signatures for multiple inputs. Attackers can shift delimiters to produce identical payloads for entirely different logical inputs.
**Prevention:** Always use length-prefixing or unambiguous delimiters (such as JSON structure or specific serialization formats) when combining multiple inputs for cryptographic hashing. For example, use `approverId.length() + ":" + approverId + extension` to strictly define the boundaries of each field.

## 2026-07-11 - XSS 취약점 제거 (`innerHTML` 사용 교체)
**Vulnerability:** `innerHTML`을 통한 동적 DOM 조작으로 인해 발생할 수 있는 DOM 기반 XSS(Cross-Site Scripting) 취약점이 발견되었습니다.
**Learning:** 로딩 상태를 표시하기 위해 버튼 내부의 텍스트와 DOM 노드를 임시로 변경하고 복구하는 과정에서 `innerHTML`을 읽고 쓰는 방식은 안전하지 않으며 정적 보안 스캐너에서 높은 위험으로 분류됩니다.
**Prevention:** 텍스트나 노드 상태를 업데이트할 때는 반드시 `Array.from(el.childNodes)`로 자식 노드를 저장하고, `el.replaceChildren(...initialChildren)`을 통해 복구하여 안전하게 처리해야 합니다.

## 2026-07-11 - 파일 이름의 널 바이트 취약점 패치
**Vulnerability:** 파일 업로드 시 파일 이름에 널 바이트(`\u0000`)를 포함할 경우, `java.nio.file.Path.of` 메서드에서 예외가 발생하여 백엔드 검증 로직이 우회되거나 예상치 못한 서비스 거부(DoS) 상태가 될 수 있습니다.
**Learning:** 파일 경로 또는 확장자 검증에서 널 바이트가 포함된 경우 잘라내기(truncation) 공격을 방지하기 위해 단순히 제거(sanitize)하는 것보다 즉시 예외를 발생시켜 입력값을 명시적으로 거부하는 것이 훨씬 안전합니다.
**Prevention:** 파일 이름 및 경로를 다루는 모든 입력값에 대해 사전에 널 바이트를 검사하고, 발견 시 `IllegalArgumentException`과 같은 예외를 던져 즉각 차단해야 합니다.

## 2026-07-12 - Prevent DoS Resource Exhaustion in Stream Hashing
**Vulnerability:** The document hashing routine in `DefaultDocumentConversionService` processed file streams without enforcing any maximum size limit on the bytes read. An attacker could exploit this by uploading a maliciously large stream (or exploiting a compression bomb if unzipping), exhausting system memory, CPU, or disk space (DoS).
**Learning:** Checking the declared file size (e.g., `file.getSize()`) in initial validation is not always sufficient if the input stream itself can be spoofed or dynamically expanded during reading. The actual bytes read must be verified against bounds continuously.
**Prevention:** Always enforce a strict, configurable size limit (e.g., `ConversionProperties.maxUploadSizeBytes`) within the `while` loop that reads from untrusted input streams. Track `totalRead` and throw an exception immediately if the limit is exceeded.

## 2026-07-17 - Missing Authentication on Admin Endpoints
**Vulnerability:** The `AdminController` endpoints (`/api/v1/admin/convert/jobs`, `DELETE /api/v1/admin/convert/jobs/{jobId}`, `POST /api/v1/admin/convert/jobs/{jobId}/retry`) were entirely lacking authentication and authorization checks, allowing unauthenticated users to access and modify administrative resources.
**Learning:** Adding a new controller without properly injecting and wiring the `TenantAccessService` to enforce permission boundaries creates a critical, unauthenticated backdoor into the application.
**Prevention:** All backend API endpoints, especially administrative ones, must enforce authentication and authorization by injecting `TenantAccessService` and executing `tenantAccessService.require(headers, TenantPermissions.[SPECIFIC_PERMISSION])` (e.g., `ADMIN_READ`, `ADMIN_WRITE`) to secure sensitive operations and prevent bypasses.
