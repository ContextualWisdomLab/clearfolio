## 2026-07-09 - Hex String Conversion Optimization
**Learning:** `String.format("%02x", b)` inside a loop is highly inefficient in Java due to continuous object allocation and parsing. Java 17+ provides `java.util.HexFormat` which is specifically optimized for this use case.
**Action:** Use `java.util.HexFormat.of().formatHex(bytes)` for byte array to hex string conversions to improve performance and reduce memory allocation.
