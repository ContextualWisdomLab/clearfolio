## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.
## 2026-07-08 - Bouncy Castle CVE-2025-14813 Mitigation
**Vulnerability:** Bouncy Castle `bcprov-jdk18on` version 1.81 contains a critical vulnerability (CVE-2025-14813) involving GOSTCTR implementation processing failures, leading to potential security issues. This was flagged by Trivy security scanner during the CI pipeline.
**Learning:** Security dependencies, particularly cryptography-related ones, require vigilant monitoring and prompt updates when critical vulnerabilities are disclosed.
**Prevention:** Ensured the exact dependency version mapping in `pom.xml` handles the vulnerable versions by explicitly updating to `1.81.1` via the `<bouncycastle.version>` property, which securely resolves the issue without breaking downstream `pdfbox` requirements.
