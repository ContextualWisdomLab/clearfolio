## 2026-06-25 - Content Hashing Optimization
**Learning:** The default SHA-256 generation using `String.format("%02x", b)` in a loop is surprisingly slow for processing byte streams.
**Action:** Use `java.util.HexFormat.of().formatHex(raw)` which is significantly faster and more memory efficient for converting bytes to hex string representations.
