## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.
## 2026-07-07 - Prevent Path Traversal in Uploaded Filenames
**Vulnerability:** The application was using `MultipartFile.getOriginalFilename()` directly without sanitization, which allowed path traversal attacks (e.g., uploading a file named `../../../etc/passwd.hwp`).
**Learning:** Filenames provided by the client in multipart requests must never be trusted. The original filename can include path traversal sequences, leading to files being saved outside the intended directory or malicious file names being used in subsequent processing.
**Prevention:** Always explicitly sanitize filenames obtained from user uploads (like `MultipartFile.getOriginalFilename()`). The path should be cleaned using `org.springframework.util.StringUtils.cleanPath()` and only the base filename should be extracted by taking the substring after the last `/`.
