1. **Update `TenantPermissions.java` to add `ADMIN_OPERATE`**
   - Provide a python script to add `public static final String ADMIN_OPERATE = "admin:operate";` to `src/main/java/com/clearfolio/viewer/auth/TenantPermissions.java`.
   - Run the script and delete it: `python3 script.py && rm -f script.py`
   - Verify the change using `cat src/main/java/com/clearfolio/viewer/auth/TenantPermissions.java`
2. **Update `AdminController.java` to enforce `admin:operate`**
   - Provide a python script to update `src/main/java/com/clearfolio/viewer/controller/AdminController.java`. The script will add `TenantAccessService` dependency, inject it in the constructor, and add `@RequestHeader final HttpHeaders headers` to all endpoint methods with corresponding `tenantAccessService.require(headers, TenantPermissions.ADMIN_OPERATE);` calls. It will also update javadoc.
   - Run the script and delete it: `python3 script.py && rm -f script.py`
   - Verify the change using `cat src/main/java/com/clearfolio/viewer/controller/AdminController.java`
3. **Update `AdminControllerTest.java` to provide headers**
   - Provide a python script to update `src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java` to construct `TenantAccessService` and use `.header("X-Tenant-ID", "tenant1")`, `.header("X-Subject-ID", "subj")`, `.header("X-Tenant-Permissions", "admin:operate")` in `webTestClient`.
   - Run the script and delete it: `python3 script.py && rm -f script.py`
   - Verify the change using `cat src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java`
4. **Append Sentinel Journal Entry**
   - Append learning to `.jules/sentinel.md` using:
```bash
cat << 'EOF_JOURNAL' >> .jules/sentinel.md
## 2023-10-24 - Missing Authorization on Admin Endpoints
**Vulnerability:** The AdminController endpoints (`/api/v1/admin/convert/jobs`, etc.) were completely unauthenticated and unauthorized, allowing anyone to list, delete, or retry conversion jobs.
**Learning:** Administrative endpoints must explicitly require an admin permission like `admin:operate` via `TenantAccessService`. Do not rely on network isolation alone.
**Prevention:** Always secure controllers with `TenantAccessService` and check for specific tenant claims on sensitive endpoints.
EOF_JOURNAL
```
   - Verify using `tail .jules/sentinel.md`
5. **Ensure Tests and Checkstyle Pass**
   - Run `mvn -B --no-transfer-progress verify` to ensure tests and checkstyle pass.
6. **Pre-commit checks**
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
