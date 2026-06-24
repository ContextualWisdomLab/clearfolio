## 2026-06-24 - Zero-allocation String Sanitization
**Learning:** String character sanitization in Java routinely incurs `StringBuilder` allocation penalties.
**Action:** Always write zero-allocation fast-paths for string sanitization where common cases are already clean, avoiding unnecessary object creation.
