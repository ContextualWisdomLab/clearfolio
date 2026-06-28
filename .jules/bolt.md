## 2026-06-24 - [MessageDigest caching]
**Learning:** In Java, creating a MessageDigest instance (`MessageDigest.getInstance("SHA-256")`) is relatively expensive and can be a bottleneck under high concurrent load. It is better to reuse instances, but they are not thread-safe. A `ThreadLocal` or cloning a prototype instance are common workarounds, but reusing instances is best. However, simply using a `MessageDigest` is O(n), no way to skip reading the whole file without changing logic.
**Action:** Follow-up: evaluate whether `contentHash` should reuse `MessageDigest` instances. This PR only changes hex formatting and does not cache `MessageDigest`.

## 2026-06-24 - [String.format to HexFormat optimization]
**Learning:** In Java, using `String.format("%02x", b)` in a loop to convert bytes to hex string is slow due to the overhead of format string parsing and new String allocations. Since Java 17, `java.util.HexFormat` provides a faster and cleaner way to format bytes to hex strings. This is relevant when hashing files where hex conversion happens on every upload.
**Action:** Replace manual `String.format` loop with `HexFormat.of().formatHex(raw)` in `contentHash` method. This makes file upload faster and reduces garbage collection pressure.
