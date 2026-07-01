## 2026-07-01 - [Null Byte Injection Prevention]
**Vulnerability:** Filename inputs missing sanitization for null bytes could be truncated, bypassing extension validation rules.
**Learning:** Checking the `file.getOriginalFilename()` for null bytes (`\u0000`) before proceeding ensures that files with hidden payloads or manipulated extensions are rejected explicitly.
**Prevention:** Always validate and reject file names that contain null bytes (`\u0000`) at the very beginning of processing, throwing an `IllegalArgumentException` rather than ignoring them.
