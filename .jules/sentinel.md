## 2026-06-29 - Missing Global API Security Headers
**Vulnerability:** API endpoints lacking basic security headers like `X-Content-Type-Options: nosniff`.
**Learning:** Security header filters (e.g., `ViewerSecurityHeadersWebFilter.java`) are sometimes overly specific in their path matching, leaving other critical API endpoints completely unprotected.
**Prevention:** Always ensure standard security headers (`X-Content-Type-Options`, `Cache-Control`) are applied globally to all responses, while reserving specific headers (like strict CSPs) for HTML-serving surfaces.
