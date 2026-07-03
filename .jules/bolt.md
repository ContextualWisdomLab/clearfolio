## 2025-02-12 - Reusable HexFormat optimization
**Learning:** Found multiple instances of byte-to-hex conversion using `String.format("%02x", b)` in a loop or calling `HexFormat.of()` repeatedly. This creates unnecessary intermediate `String` and `Object[]` allocations, decreasing performance.
**Action:** Use a static final reusable `java.util.HexFormat.of()` instance for byte array to hex string conversions in the codebase to eliminate redundant memory allocations and reduce GC overhead.
