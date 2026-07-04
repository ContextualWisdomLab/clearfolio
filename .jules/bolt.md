## 2024-05-18 - HexFormat Performance
**Learning:** `String.format("%02x", b)` inside a loop to convert a byte array to a hex string is a significant performance bottleneck due to excessive intermediate String allocations and parsing overhead. Using a reusable `java.util.HexFormat` instance (e.g., `HexFormat.of().formatHex(bytes)`) is much more efficient.
**Action:** Replace `String.format` byte-to-hex conversions with `HexFormat.of().formatHex(byte[])`.
