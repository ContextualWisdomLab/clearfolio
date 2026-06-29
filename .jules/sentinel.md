## 2024-06-26 - [Null Byte Bypass in File Extension Validation]
**Vulnerability:** File extension validation was vulnerable to null byte injection (e.g. `file.hwp\u0000`).
**Learning:** `String.strip()` does not remove null characters, leaving them at the end of the extracted extension, bypassing exact match checks in sets (like `blockedExtensions.contains(extension)`).
**Prevention:** Always explicitly remove or reject null bytes (`\u0000`) before performing file extension extraction or validation, as many string normalization functions only target whitespace.
