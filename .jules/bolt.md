## 2024-06-27 - HexFormat Optimization
**Learning:** Using `String.format("%02x", b)` in a loop for byte to hex conversion creates significant overhead due to excessive object allocations and parsing.
**Action:** Always prefer `java.util.HexFormat.of().formatHex(raw)` introduced in Java 17 for byte array to hex string conversions, as it is highly optimized and avoids unnecessary object creation.
