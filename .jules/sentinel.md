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
