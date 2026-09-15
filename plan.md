1. **View specific lines of `AdminController.java` to prepare search and replace**:
    - Run `sed -n '15,50p' src/main/java/com/clearfolio/viewer/controller/AdminController.java` to inspect exact lines for the constructor and imports.
    - Run `sed -n '50,90p' src/main/java/com/clearfolio/viewer/controller/AdminController.java` to inspect exact lines for the methods.

2. **Modify `TenantPermissions.java`**:
    - Execute a bash script to replace the file content with the newly added `ADMIN_READ` and `ADMIN_WRITE` permissions.
    ```bash
    cat << 'EOF' > update_permissions.py
    with open('src/main/java/com/clearfolio/viewer/auth/TenantPermissions.java', 'r') as f:
        content = f.read()

    search = """    /**
     * Permission required to read buyer-demo analytics.
     */
    public static final String ANALYTICS_READ = "analytics:read";

    private TenantPermissions() {
    }"""
    replace = """    /**
     * Permission required to read buyer-demo analytics.
     */
    public static final String ANALYTICS_READ = "analytics:read";

    /**
     * Permission required for admin-level read operations.
     */
    public static final String ADMIN_READ = "admin:read";

    /**
     * Permission required for admin-level write operations.
     */
    public static final String ADMIN_WRITE = "admin:write";

    private TenantPermissions() {
    }"""
    content = content.replace(search, replace)
    with open('src/main/java/com/clearfolio/viewer/auth/TenantPermissions.java', 'w') as f:
        f.write(content)
    EOF
    python3 update_permissions.py
    rm update_permissions.py
    ```

3. **Verify `TenantPermissions.java` modification**:
    - Run `cat src/main/java/com/clearfolio/viewer/auth/TenantPermissions.java` to confirm the changes.

4. **Modify `AdminController.java`**:
    - Execute a bash script to update imports, add `TenantAccessService` to the constructor, and update endpoints to require headers and perform access control.
    ```bash
    cat << 'EOF' > update_admin.py
    with open('src/main/java/com/clearfolio/viewer/controller/AdminController.java', 'r') as f:
        content = f.read()

    search_import = """import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;"""
    replace_import = """import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.server.ResponseStatusException;

import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;"""
    content = content.replace(search_import, replace_import)

    search_constructor = """public class AdminController {

    private final DocumentConversionService conversionService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     */
    public AdminController(DocumentConversionService conversionService) {
        this.conversionService = conversionService;
    }"""
    replace_constructor = """public class AdminController {

    /**
     * Service for document conversion operations.
     */
    private final DocumentConversionService conversionService;

    /**
     * Service for enforcing tenant access and permissions.
     */
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     * @param tenantAccessService tenant access service
     */
    public AdminController(DocumentConversionService conversionService, TenantAccessService tenantAccessService) {
        this.conversionService = conversionService;
        this.tenantAccessService = tenantAccessService;
    }"""
    content = content.replace(search_constructor, replace_constructor)

    search_methods = """    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(@RequestParam(required = false) Boolean deadLettered) {
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        if (deadLettered == null) {
            return AdminJobListResponse.from(allJobs);
        }

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.isDeadLettered() == deadLettered) {
                filtered.add(job);
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID jobId) {
        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(@PathVariable UUID jobId) {
        RetryDeadLetterResult result = conversionService.retryDeadLettered(jobId, "admin");
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }"""
    replace_methods = """    /**
     * Retrieves all conversion jobs, optionally filtered by dead-letter status.
     *
     * @param deadLettered optional filter for dead-lettered jobs
     * @param headers request headers carrying tenant claims
     * @return list of conversion jobs
     */
    @GetMapping("/api/v1/admin/convert/jobs")
    public AdminJobListResponse getAllJobs(
            @RequestParam(required = false) Boolean deadLettered,
            @RequestHeader HttpHeaders headers) {
        tenantAccessService.require(headers, TenantPermissions.ADMIN_READ);
        Iterable<ConversionJob> allJobs = conversionService.getAllJobs();

        if (deadLettered == null) {
            return AdminJobListResponse.from(allJobs);
        }

        List<ConversionJob> filtered = new ArrayList<>();
        for (ConversionJob job : allJobs) {
            if (job.isDeadLettered() == deadLettered) {
                filtered.add(job);
            }
        }
        return AdminJobListResponse.from(filtered);
    }

    /**
     * Deletes a conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return no content on success
     */
    @DeleteMapping("/api/v1/admin/convert/jobs/{jobId}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable UUID jobId,
            @RequestHeader HttpHeaders headers) {
        tenantAccessService.require(headers, TenantPermissions.ADMIN_WRITE);
        conversionService.deleteJob(jobId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retries a dead-lettered conversion job.
     *
     * @param jobId conversion job identifier
     * @param headers request headers carrying tenant claims
     * @return accepted response on success
     */
    @PostMapping("/api/v1/admin/convert/jobs/{jobId}/retry")
    public ResponseEntity<Void> retryDeadLettered(
            @PathVariable UUID jobId,
            @RequestHeader HttpHeaders headers) {
        TenantContext tenantContext = tenantAccessService.require(headers, TenantPermissions.ADMIN_WRITE);
        RetryDeadLetterResult result = conversionService.retryDeadLettered(jobId, tenantContext.subjectId());
        if (result == RetryDeadLetterResult.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found");
        }
        if (result == RetryDeadLetterResult.NOT_ELIGIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not eligible for retry");
        }
        return ResponseEntity.accepted().build();
    }"""
    content = content.replace(search_methods, replace_methods)

    with open('src/main/java/com/clearfolio/viewer/controller/AdminController.java', 'w') as f:
        f.write(content)
    EOF
    python3 update_admin.py
    rm update_admin.py
    ```

5. **Verify `AdminController.java` modification**:
    - Run `cat src/main/java/com/clearfolio/viewer/controller/AdminController.java` to confirm the changes.

6. **View specific lines of `AdminControllerTest.java` to prepare search and replace**:
    - Run `sed -n '15,40p' src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java` to inspect exact lines for the constructor and test setup.

7. **Modify `AdminControllerTest.java`**:
    - Execute a bash script to mock `TenantAccessService` and update tests.
    ```bash
    cat << 'EOF' > update_admin_test.py
    with open('src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java', 'r') as f:
        content = f.read()

    search_import = """import com.clearfolio.viewer.service.RetryDeadLetterResult;"""
    replace_import = """import com.clearfolio.viewer.service.RetryDeadLetterResult;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantContext;
import com.clearfolio.viewer.auth.TenantPermissions;
import org.springframework.http.HttpHeaders;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import java.util.Set;"""
    content = content.replace(search_import, replace_import)

    search_setup = """    private DocumentConversionService conversionService;
    private WebTestClient webTestClient;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        controller = new AdminController(conversionService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }"""
    replace_setup = """    private DocumentConversionService conversionService;
    private TenantAccessService tenantAccessService;
    private WebTestClient webTestClient;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        conversionService = mock(DocumentConversionService.class);
        tenantAccessService = mock(TenantAccessService.class);

        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_READ)))
                .thenReturn(new TenantContext("admin-tenant", "admin-user", Set.of(TenantPermissions.ADMIN_READ)));
        when(tenantAccessService.require(any(HttpHeaders.class), eq(TenantPermissions.ADMIN_WRITE)))
                .thenReturn(new TenantContext("admin-tenant", "admin-user", Set.of(TenantPermissions.ADMIN_WRITE)));

        controller = new AdminController(conversionService, tenantAccessService);
        webTestClient = WebTestClient.bindToController(controller)
                .controllerAdvice(new ApiExceptionHandler())
                .build();
    }"""
    content = content.replace(search_setup, replace_setup)

    search_test1 = """when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.ACCEPTED);"""
    replace_test1 = """when(conversionService.retryDeadLettered(jobId, "admin-user")).thenReturn(RetryDeadLetterResult.ACCEPTED);"""
    content = content.replace(search_test1, replace_test1)

    search_test2 = """when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_FOUND);"""
    replace_test2 = """when(conversionService.retryDeadLettered(jobId, "admin-user")).thenReturn(RetryDeadLetterResult.NOT_FOUND);"""
    content = content.replace(search_test2, replace_test2)

    search_test3 = """when(conversionService.retryDeadLettered(jobId, "admin")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);"""
    replace_test3 = """when(conversionService.retryDeadLettered(jobId, "admin-user")).thenReturn(RetryDeadLetterResult.NOT_ELIGIBLE);"""
    content = content.replace(search_test3, replace_test3)

    with open('src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java', 'w') as f:
        f.write(content)
    EOF
    python3 update_admin_test.py
    rm update_admin_test.py
    ```

8. **Verify `AdminControllerTest.java` modification**:
    - Run `cat src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java` to confirm changes.

9. **Update Journal File**:
    - Update the journal file with a critical security learning.
    ```bash
    echo -e "## $(date +%Y-%m-%d) - [Admin Endpoints Missing Access Controls]\n**Vulnerability:** The AdminController exposed multiple admin endpoints without enforcing authentication or authorization, allowing anyone to view, delete, or retry jobs.\n**Learning:** Global endpoints that span across isolated boundaries (like tenant jobs) must have explicit admin-level permissions instead of skipping auth.\n**Prevention:** Ensure that all endpoints inject TenantAccessService or equivalent guard checks with explicitly modeled permissions during controller setup." >> .jules/sentinel.md
    ```

10. **Verify Journal Update**:
    - Run `cat .jules/sentinel.md` to confirm the file has the new entry.

11. **Run Tests and Format Checks**:
    - Run `mvn -B test` to ensure all tests pass and coverage is maintained.
    - Run `mvn -B checkstyle:check` to verify styling.

12. **Complete Pre-Commit Steps**:
    - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.

13. **Submit the PR**:
    - Run `git commit -m "🛡️ Sentinel: [CRITICAL] Fix Admin Authentication"` and use `submit` with Korean commit messages to create a PR.
