## 2026-09-30 - Add Authentication to Admin Endpoints
**Vulnerability:** Admin endpoints (`AdminController.java`) are completely open without any authentication or authorization checks.
**Learning:** Controller classes meant for internal administration were not integrated with the `TenantAccessService` used across other protected controllers (like `ConversionController`), leading to insecure direct access to jobs.
**Prevention:** Always verify that every controller endpoint is protected by appropriate authorization checks, like `TenantAccessService.require(headers, permission)`.

## 2026-09-30 - Add Authentication to Admin Endpoints
**Vulnerability:** Admin endpoints (`AdminController.java`) are completely open without any authentication or authorization checks.
**Learning:** Controller classes meant for internal administration were not integrated with the `TenantAccessService` used across other protected controllers (like `ConversionController`), leading to insecure direct access to jobs.
**Prevention:** Always verify that every controller endpoint is protected by appropriate authorization checks, like `TenantAccessService.require(headers, permission)`.
