## 2026-06-28 - Fast Hex String Conversion
**Learning:** Using `String.format("%02x", b)` inside a loop for converting byte arrays to hex strings is a significant performance bottleneck due to string parsing overhead per byte.
**Action:** Use `java.util.HexFormat.of().formatHex(bytes)` instead for a massive performance improvement when converting byte arrays to hex strings in Java 17+.
