## 2026-07-02 - String.format in loops
**Learning:** `String.format` is slow for hex conversion inside loops.
**Action:** Replace it with `HexFormat.of().formatHex(bytes)` which is significantly faster and doesn't do intermediate string creations.
