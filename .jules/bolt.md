## 2026-07-01 - Byte Array to Hex String Conversion Optimization
**Learning:** In Java 21, using `String.format("%02x", b)` inside a loop to convert a byte array to a hex string causes unnecessary allocations and is significantly slower than native alternatives.
**Action:** Use `java.util.HexFormat.of().formatHex(bytes)` for faster and allocation-free conversions from byte arrays to hex strings in this codebase.
