## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.

## 2026-07-08 - Prevent Null Byte Injection in File Validation
**Vulnerability:** The document validation service relied on the original filename without verifying the presence of null bytes (`\u0000`). This allows attackers to bypass file extension checks via truncation (e.g., `malicious.hwp\u0000.pdf`).
**Learning:** Checking the file extension using standard string operations can be bypassed if the underlying system API (like a C-based filesystem or a library handling byte arrays) truncates the string at the first null byte.
**Prevention:** Explicitly check for the presence of null bytes (`\u0000`) in the filename as early as possible and throw an exception to reject the input entirely, rather than attempting to sanitize it.
