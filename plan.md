1. **Explore `TenantPermissions.java` and `TenantAccessService.java`**
   - The memory states: "Administrative endpoints must be secured using `TenantAccessService` and an explicit cross-tenant admin permission (e.g., `admin:operate`) passed via request headers to prevent unauthorized access."
   - Currently, `AdminController.java` lacks any authentication or authorization checks.
2. **Update `TenantPermissions.java`**
   - Add the `ADMIN_OPERATE` permission as `"admin:operate"`.
3. **Update `AdminController.java`**
   - Inject `TenantAccessService` via constructor.
   - For every endpoint, read the `@RequestHeader HttpHeaders headers` and check `tenantAccessService.require(headers, TenantPermissions.ADMIN_OPERATE)`.
4. **Update `AdminControllerTest.java`**
   - Add the new headers to requests or mock `TenantAccessService`.
5. **Pre-commit checks**
   - Ensure the required testing, format, lint, and verification processes are complete.
