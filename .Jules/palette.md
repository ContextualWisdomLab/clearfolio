## 2026-06-29 - Target Blank Attributes Context Preservation
**Learning:** Adding `target="_blank"` improves the UX flow but must always be accompanied by `rel="noopener noreferrer"` to prevent Reverse Tabnabbing security risks when opening unverified JSON or artifact blobs.
**Action:** Always include both attributes together when opening external views to ensure the user does not lose their current context securely.
