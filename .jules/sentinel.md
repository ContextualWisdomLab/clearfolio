## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.

## 2026-07-02 - Cryptographic Signature Verification Bypass in Policy Override
**Vulnerability:** The document validation service logged the presence of policy override parameters (approverId, approvalToken) but failed to actually verify the cryptographic signature of the token against a shared secret. This allowed an attacker to bypass file extension restrictions (e.g., uploading blocked `.hwp` files) by sending any arbitrary token.
**Learning:** Checking for the presence of security tokens is insufficient if the token payload and signature are not cryptographically validated against a trusted secret. The absence of this check created a critical authorization bypass.
**Prevention:** Always verify cryptographic signatures (using constant-time comparison like `MessageDigest.isEqual`) for any policy override or authorization token before granting the elevated privilege or bypassing a security control.

## 2026-07-08 - Length Extension and Canonicalization Vulnerability in Hash Payloads
**Vulnerability:** The HMAC-SHA256 signature payload for policy overrides was constructed by simply concatenating strings: `approverId + ":" + extension`. This allowed attackers to craft ambiguous inputs if they embedded the delimiter `:` inside their payload, potentially bypassing validation via canonicalization or length extension attacks.
**Learning:** Simple string concatenation is insecure when generating cryptographic hashes or signatures for multiple inputs. Attackers can shift delimiters to produce identical payloads for entirely different logical inputs.
**Prevention:** Always use length-prefixing or unambiguous delimiters (such as JSON structure or specific serialization formats) when combining multiple inputs for cryptographic hashing. For example, use `approverId.length() + ":" + approverId + extension` to strictly define the boundaries of each field.
## 2026-07-11 - 파일 이름의 널 바이트 취약점 패치
**Vulnerability:** 파일 업로드 시 파일 이름에 널 바이트(`\u0000`)를 포함할 경우, `java.nio.file.Path.of` 메서드에서 예외가 발생하여 백엔드 검증 로직이 우회되거나 예상치 못한 서비스 거부(DoS) 상태가 될 수 있습니다.
**Learning:** 파일 경로 또는 확장자 검증에서 널 바이트가 포함된 경우 잘라내기(truncation) 공격을 방지하기 위해 단순히 제거(sanitize)하는 것보다 즉시 예외를 발생시켜 입력값을 명시적으로 거부하는 것이 훨씬 안전합니다.
**Prevention:** 파일 이름 및 경로를 다루는 모든 입력값에 대해 사전에 널 바이트를 검사하고, 발견 시 `IllegalArgumentException`과 같은 예외를 던져 즉각 차단해야 합니다.
