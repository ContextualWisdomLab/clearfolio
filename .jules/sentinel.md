## 2026-06-29 - [HTML Template XSS Defense in Depth]
**Vulnerability:** Inline HTML template string replacements (`String.replace()`) in Java controllers without HTML escaping.
**Learning:** Even if current inputs to a `String.replace()` template are safe (like a UUID), treating templates this way introduces a significant risk of XSS if inputs become user-controlled later.
**Prevention:** Implement a lightweight custom `escapeHtml` function and wrap all dynamically injected strings before replacing them in the template. Ensure all parameters of the newly added method conform to Checkstyle rules (e.g., explicitly marked as `final`).
