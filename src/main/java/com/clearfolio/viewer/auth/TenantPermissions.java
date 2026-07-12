package com.clearfolio.viewer.auth;

/**
 * Permission names enforced by Clearfolio API endpoints.
 */
public final class TenantPermissions {

    /**
     * Permission required to submit a conversion job.
     */
    public static final String JOB_CREATE = "job:create";

    /**
     * Permission required to read conversion job status.
     */
    public static final String JOB_READ = "job:read";

    /**
     * Permission required to retry a dead-lettered conversion job.
     */
    public static final String JOB_RETRY = "job:retry";

    /**
     * Permission required to read viewer bootstrap payloads.
     */
    public static final String VIEWER_READ = "viewer:read";

    /**
     * Permission required to create signed artifact links.
     */
    public static final String ARTIFACT_LINK_CREATE = "artifact-link:create";

    /**
     * Permission required to revoke signed artifact links.
     */
    public static final String ARTIFACT_LINK_REVOKE = "artifact-link:revoke";

    /**
     * Permission required to read audit evidence.
     */
    public static final String AUDIT_READ = "audit:read";

    /**
     * Permission required to read buyer-demo analytics.
     */
    public static final String ANALYTICS_READ = "analytics:read";

    private TenantPermissions() {
    }
}
