## 2026-06-24 - Zero-allocation String Sanitization
**Learning:** String character sanitization in Java routinely incurs `StringBuilder` allocation penalties.
**Action:** Prefer allocation-conscious fast-paths only when they preserve the full security and correctness requirements of the sanitizer.
