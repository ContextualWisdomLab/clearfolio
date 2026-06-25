## 2026-06-25 - External Link Context & Security
**Learning:** External links or links pointing to raw data outputs (like JSON endpoints) can unexpectedly rip users out of the main viewing context. Opening them in new tabs requires security attributes (`rel="noopener noreferrer"`) to prevent the new tab from potentially manipulating the original page's window object.
**Action:** Always add `target="_blank"` paired with `rel="noopener noreferrer"` to external resource links or bootstrap JSON viewers to preserve the primary document preview session.
