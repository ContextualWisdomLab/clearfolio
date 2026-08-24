1. **Fix tests breaking coverage by hitting missing branches in `AdminController.java`**:
    - The `getAllJobs` method has `continue;` if the job doesn't belong to the tenant. The `AdminControllerTest` needs a test case that covers this branch, which means we need a mock job that doesn't belong to `admin-tenant`. We already added one, let's verify if `AdminControllerTest.java` is correctly asserting its filtered result. Wait, in `getAllJobsReturnsAllJobsWhenNoFilterProvided`, the length asserted is 2, and the third job is filtered out. That should cover it. Let's run Jacoco report locally.

    - Actually, Jacoco failed in CI because `branches missed count is 1, but expected maximum is 0`. Let's check `AdminController.java` jacoco missed branches.
    - Wait, the error is: `com/clearfolio/viewer/controller/AdminController.java:69: missed_instructions=0 missed_branches=1`.
    Line 69 is: `if (!job.belongsToTenant(tenantContext.tenantId())) {`.
    If this condition is true, it continues. If false, it proceeds. The test `getAllJobsReturnsAllJobsWhenNoFilterProvided` adds `jobOtherTenant` where it doesn't belong, so `belongsToTenant` returns false, making the condition true. So it covers the true branch. But do we have a case where `jobOtherTenant` isn't there, or `deadLettered` filtering does something else?
    Actually, wait. The test `getAllJobsReturnsAllJobsWhenNoFilterProvided` covers `belongsToTenant` true (job1, job2) and false (`jobOtherTenant`). Why would it miss a branch? Let's check the test for `getAllJobsFiltersByDeadLetteredTrue` and `getAllJobsFiltersByDeadLetteredFalse`. They don't have `jobOtherTenant`. So the condition `!job.belongsToTenant(...)` is only true in the first test. It's false in all tests for the valid jobs. That means both branches are covered.
    Wait, what about line 70? `continue;`.
    Let's look at `AdminController.java` line 69-72:
    ```java
        for (ConversionJob job : allJobs) {
            if (!job.belongsToTenant(tenantContext.tenantId())) {
                continue;
            }
            if (deadLettered == null || job.isDeadLettered() == deadLettered) {
    ```
    Is there an `Optional` in `deleteJob`?
    `tenantAccessService.requireSameTenant(tenantContext, conversionService.getJob(jobId).orElse(null));`
    This `orElse(null)` evaluates to null if not present. Have we tested the case where `getJob` returns `Optional.empty()`?
    Yes, let's look at `AdminControllerTest.java` `deleteJobReturnsNoContent`. It mocks `getJob` returning `Optional.of(job)`. We need a test for `Optional.empty()`. Wait, if `Optional.empty()`, `deleteJob` passes `null` to `requireSameTenant`, which throws `ResponseStatusException(HttpStatus.NOT_FOUND)`. But we haven't tested this exception path. This is a missed branch! `Optional.empty()` is a missed branch for `deleteJob` and `retryDeadLettered`.
    Wait, the Jacoco error explicitly says `com/clearfolio/viewer/controller/AdminController.java:69: missed_instructions=0 missed_branches=1`. And line 70 is `missed_instructions=1 missed_branches=0`.
    Ah! The error is explicitly on line 69 and 70!
    Line 69: `if (!job.belongsToTenant(tenantContext.tenantId())) {`
    Line 70: `continue;`
    Wait, if `missed_instructions=1` on line 70, it means `continue;` is NEVER executed!
    Why wasn't `continue;` executed? In my previous test update, I added `jobOtherTenant`, but did I add it to `allJobs`?
    Let's check `AdminControllerTest.java` again.
