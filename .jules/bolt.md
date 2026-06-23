## 2026-06-23 - [Optimization target identification: String.format vs HexFormat]
**Learning:** In Java 17+, `String.format("%02x", b)` inside a loop is a significant performance bottleneck for converting byte arrays to hex strings due to repetitive pattern parsing and object creation per byte.
**Action:** Always prefer `java.util.HexFormat.of().formatHex(byteArray)` which provides a highly optimized, allocation-efficient conversion.
