## 2026-06-28 - [Null Byte Injection Bypass in File Extension Validation]
**Vulnerability:** A file named `malicious.hwp\0.pdf` bypasses the `blockedExtensions` check (which verifies against `.pdf`) in `DefaultDocumentValidationService`, but could be subsequently processed as `.hwp` by systems or libraries written in C/C++ that terminate strings at the null byte.
**Learning:** Checking the extension after the last dot does not secure the system if the file name contains a null byte, which is ignored in Java but terminates strings in C/C++.
**Prevention:** Explicitly validate all uploaded file names against null bytes before performing extension extraction or other validation checks.
