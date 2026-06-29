## 2026-06-29 - Prevent javascript: URI XSS in preview link
**Vulnerability:** XSS via `javascript:` URIs in `renderPreviewLink`.
**Learning:** Even when avoiding `innerHTML`, directly setting `a.href = untrusted_path` can lead to Cross-Site Scripting (XSS) if the path uses a `javascript:` or `data:` protocol and the user clicks the link.
**Prevention:** Always validate and sanitize URLs before assigning them to `href` or `src` attributes, ensuring they only use safe protocols like `http:`, `https:`, or relative paths.
