## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.
## 2026-07-08 - Fix Strix Penetration Test Vulnerability
**Vulnerability:** Truncated Hash in Token Fingerprint. The `tokenFingerprint` method truncated SHA-256 hashes to 8 bytes, reducing security and increasing collision risks.
**Learning:** Truncating cryptographic hashes can compromise the strength of the hashing algorithm and increase the risk of collision attacks.
**Prevention:** Use the full hash output for validation or cryptographic purposes to maintain security strength.
