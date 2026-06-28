## 2026-06-23 - Java Hex Conversion Optimization
**Learning:** In Java 17+, `HexFormat.of().formatHex(byte[])` is drastically faster (~100x) than manually looping over a byte array and appending `String.format("%02x", b)` to a `StringBuilder`, as it avoids regex parsing and repeated object allocation.
**Action:** Always prefer `java.util.HexFormat` for any byte array to hex string conversions in the codebase.
