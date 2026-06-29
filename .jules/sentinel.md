## 2026-06-29 - [DOM XSS Prevention in Frontend Asset]
**Vulnerability:** DOM-based XSS via `previewResourcePath` rendered in `viewer.js` links and iframes without URL scheme validation.
**Learning:** Even though backend validation protects paths locally, the frontend directly accepts untrusted path payloads from JSON, which requires defense-in-depth protection using the native `URL` constructor to enforce safe protocols.
**Prevention:** Always parse untrusted URLs using `new URL(url, window.location.href)` and restrict `.protocol` to `http:` and `https:` instead of relying solely on `startsWith` pattern matching.
