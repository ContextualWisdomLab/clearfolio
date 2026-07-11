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

## 2026-07-11 - XSS 취약점 제거 (`innerHTML` 사용 교체)
**Vulnerability:** `innerHTML`을 통한 동적 DOM 조작으로 인해 발생할 수 있는 DOM 기반 XSS(Cross-Site Scripting) 취약점이 발견되었습니다.
**Learning:** 로딩 상태를 표시하기 위해 버튼 내부의 텍스트와 DOM 노드를 임시로 변경하고 복구하는 과정에서 `innerHTML`을 읽고 쓰는 방식은 안전하지 않으며 정적 보안 스캐너에서 높은 위험으로 분류됩니다.
**Prevention:** 텍스트나 노드 상태를 업데이트할 때는 반드시 `Array.from(el.childNodes)`로 자식 노드를 저장하고, `el.replaceChildren(...initialChildren)`을 통해 복구하여 안전하게 처리해야 합니다.
