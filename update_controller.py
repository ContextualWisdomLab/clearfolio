with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "r") as f:
    content = f.read()

content = content.replace(
"""import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;""",
"""import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;""")

content = content.replace(
"""import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;""",
"""import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;""")

content = content.replace(
"""import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.model.ConversionJob;""",
"""import com.clearfolio.viewer.api.AdminJobListResponse;
import com.clearfolio.viewer.auth.TenantAccessService;
import com.clearfolio.viewer.auth.TenantPermissions;
import com.clearfolio.viewer.model.ConversionJob;""")

content = content.replace(
"""public class AdminController {

    private final DocumentConversionService conversionService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     */
    public AdminController(DocumentConversionService conversionService) {
        this.conversionService = conversionService;
    }""",
"""public class AdminController {

    private final DocumentConversionService conversionService;
    private final TenantAccessService tenantAccessService;

    /**
     * Creates a controller for admin operations.
     *
     * @param conversionService conversion service
     * @param tenantAccessSvc tenant access service
     */
    public AdminController(
            final DocumentConversionService conversionService,
            final TenantAccessService tenantAccessSvc) {
        this.conversionService = conversionService;
        this.tenantAccessService = tenantAccessSvc;
    }""")

content = content.replace(
"""    public AdminJobListResponse getAllJobs(@RequestParam(required = false) Boolean deadLettered) {""",
"""    public AdminJobListResponse getAllJobs(
            @RequestHeader final HttpHeaders headers,
            @RequestParam(required = false) final Boolean deadLettered) {
        tenantAccessService.require(headers, TenantPermissions.JOB_READ);""")

content = content.replace(
"""    public ResponseEntity<Void> deleteJob(@PathVariable UUID jobId) {""",
"""    public ResponseEntity<Void> deleteJob(
            @RequestHeader final HttpHeaders headers,
            @PathVariable final UUID jobId) {
        tenantAccessService.require(headers, TenantPermissions.JOB_DELETE);""")

content = content.replace(
"""    public ResponseEntity<Void> retryDeadLettered(@PathVariable UUID jobId) {""",
"""    public ResponseEntity<Void> retryDeadLettered(
            @RequestHeader final HttpHeaders headers,
            @PathVariable final UUID jobId) {
        tenantAccessService.require(headers, TenantPermissions.JOB_RETRY);""")

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "w") as f:
    f.write(content)
