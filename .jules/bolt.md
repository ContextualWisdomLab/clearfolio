## 2026-07-09 - Reuse HexFormat instance to reduce allocations and loops
**Learning:** Using `String.format("%02x", b)` in a loop or calling `HexFormat.of()` repeatedly for hex conversions creates unnecessary intermediate object allocations and reduces performance.
**Action:** Always use a single, reusable `private static final java.util.HexFormat HEX_FORMAT = java.util.HexFormat.of();` instance and its `formatHex` method for byte-to-hex string conversions.
