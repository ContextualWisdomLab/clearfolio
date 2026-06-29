## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.

## 2026-06-29 - [DOM XSS Prevention in Frontend Asset]
**Vulnerability:** DOM-based XSS via `previewResourcePath` rendered in `viewer.js` links and iframes without URL scheme validation.
**Learning:** Even though backend validation protects paths locally, the frontend directly accepts untrusted path payloads from JSON, which requires defense-in-depth protection using the native `URL` constructor to enforce safe protocols.
**Prevention:** Always parse untrusted URLs using `new URL(url, window.location.href)` and restrict `.protocol` to `http:` and `https:` instead of relying solely on `startsWith` pattern matching.
