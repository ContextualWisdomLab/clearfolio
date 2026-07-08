## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.

## 2026-07-08 - Prevent Directory Traversal via MultipartFile.getOriginalFilename()
**Vulnerability:** Filenames obtained from user uploads (e.g., `MultipartFile.getOriginalFilename()`) were used directly without sanitization, exposing a directory traversal vector.
**Learning:** `getOriginalFilename()` can return paths containing directory traversal characters (e.g. `../`) from malicious or misconfigured clients. Trusting this value without explicit sanitation can lead to arbitrary file creation/access or corruption of data depending on its usage down the line.
**Prevention:** Explicitly sanitize filenames obtained from user uploads using `org.springframework.util.StringUtils.cleanPath()` and extracting only the base filename by taking the substring after the last `/`.
