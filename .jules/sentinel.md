## 2026-06-30 - Prevent DOM-based XSS in Viewer JS
**Vulnerability:** Untrusted paths from API responses were directly assigned to `a.href` and used in `iframe` generation, which allows execution of malicious URIs like `javascript:` or `data:`.
**Learning:** Even when avoiding `innerHTML`, directly setting URL-like strings to DOM attributes without protocol validation introduces XSS vectors. The payload can be executed when the link is clicked or the iframe is loaded.
**Prevention:** Implement an `isSafeUrl` verification function to ensure the protocol is strictly `http:` or `https:` (using `new URL()`) before assigning untrusted inputs to DOM attributes like `href` or `src`.

## 2026-07-02 - Fix Null Byte Injection in File Validation
**Vulnerability:** File extension validation was vulnerable to null byte injection (`\u0000`) in filenames (e.g. `malicious.exe\0.png`), potentially bypassing format restrictions.
**Learning:** Checking the extension using `lastIndexOf('.')` after the null byte might allow harmful files to bypass the blocklist because backend file systems or execution contexts could interpret the filename up to the null byte.
**Prevention:** Always validate input file names for null bytes and explicitly reject them before proceeding with extension parsing.

## 2026-07-07 - Fix Sensitive Data Logging and Insufficient Fingerprint Length in Validation Service
**Vulnerability:** The document validation service was logging potentially sensitive data (`approverId` for policy overrides) and using a short 8-byte hash for the token fingerprint.
**Learning:** Overly short cryptographic hashes are susceptible to collision attacks, and logging raw identifier fields like `approverId` alongside audit data can leak sensitive information.
**Prevention:** Avoid logging unneeded sensitive strings in operational logs and increase cryptographic fingerprint lengths (e.g. from 8 bytes to 16 bytes).

## 2026-07-07 - Upgrade Dependency Or Exclude Vulnerable Modules
**Vulnerability:** `org.bouncycastle:bcprov-jdk18on` has a CRITICAL CVE (CVE-2025-14813) that causes the trivy-fs job to fail.
**Learning:** BouncyCastle modules were included as transitive dependencies via `tika-parsers-standard-package`.
**Prevention:** To satisfy trivy security scans, vulnerable transitive dependencies should be excluded in `pom.xml` if they are not necessary or upgraded to a secure version.
