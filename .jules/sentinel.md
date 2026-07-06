## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.
## 2026-07-06 - Prevent Truncation Bypass Attacks via Null Bytes
**Vulnerability:** File validation logic (`DefaultDocumentValidationService.java`) failed to properly check for null bytes (`\u0000`) in filenames, relying on a naive replace/sanitize step in a different method that didn't prevent an attacker from bypassing the extension check.
**Learning:** Never silently sanitize or truncate input when performing security validations like file extensions. Attackers can use null bytes (`.hwp\0.pdf`) to trick the extension check (`.pdf`) while the underlying OS or downstream services process the truncated file (`.hwp`).
**Prevention:** Explicitly reject any input containing a null byte by throwing an exception immediately. Do not attempt to strip or sanitize it.
