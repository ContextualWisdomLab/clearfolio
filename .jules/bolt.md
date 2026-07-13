## 2026-07-13 - [Optimize String Allocation for Sanitization]
**Learning:** Chained `.replace()` calls or unconditionally allocating new strings/char arrays creates excessive garbage collection overhead, especially in frequent operations like logging.
**Action:** Use `charAt()` to scan the string first to check if any modifications are strictly necessary. Return the original string if the happy path does not require changes, and only allocate a `StringBuilder` or char array if a modification is needed.
