## 2026-07-05 - Use HexFormat over String.format for better performance
**Learning:** In Java 17+, `java.util.HexFormat.of().formatHex(bytes)` is significantly faster and allocates less memory than looping through a byte array and using `String.format("%02x", b)` for each byte.
**Action:** Always prefer `HexFormat` for byte array to hex string conversions in Java 17+ codebases.
