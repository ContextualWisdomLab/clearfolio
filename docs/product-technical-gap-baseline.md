# Product–Technical Gap Baseline

This baseline records buyer-visible and architectural gaps verified against the live `clearfolio` code. It is deliberately evidence-bound: an item is not marked complete until its exact PR head has passed the repository's required checks and independent review.

## Current security boundary: conversion administration

### Problem

`AdminController` exposes list, delete, and dead-letter retry operations over `ConversionJob` aggregates. The protected baseline accepted these operations without `TenantAccessService` authorization. Adding only an admin permission check is insufficient because `TenantContext.tenantId` is the data-isolation boundary and `DocumentConversionService.getAllJobs()` is intentionally global.

A permission-bearing caller must therefore still be prevented from observing or mutating another tenant's `ConversionJob`.

### Domain contract

- Bounded context: document conversion operations.
- Aggregate: `ConversionJob`.
- Tenant ownership invariant: `ConversionJob.belongsToTenant(TenantContext.tenantId())` must hold before a tenant-scoped administrative operation can expose or mutate the aggregate.
- Authorization boundary: `TenantAccessService.require(...)` validates signed request claims and the required capability; ownership is a separate invariant.
- Delete boundary: use the existing tenant-aware `DocumentConversionService.deleteJob(jobId, tenantContext)` contract so foreign and missing jobs are indistinguishable to the caller.
- Retry boundary: load the aggregate, apply `TenantAccessService.requireSameTenant(...)`, then attribute the retry to the verified `TenantContext.subjectId()` rather than a synthetic operator name.
- List boundary: filter global service results by the verified tenant before applying presentation filters such as dead-letter state.

### TDD evidence

PR #568 contains a test-first descendant that adds cross-tenant list/delete/retry cases. Those tests require foreign tenant data to be absent from list responses, destructive operations to return `404` for missing or foreign aggregates, and retry audit attribution to use the verified subject. The production descendant then implements the minimum controller-side ownership enforcement and removes the one-shot `fix_javadoc.py` source-rewriting helper.

The candidate implementation is not release-complete until the unchanged exact head passes application tests, static/security checks, central CodeQL settlement where applicable, and qualifying independent review.

### Buyer-visible effect

An operator with administrative permissions for one tenant must not gain visibility into another tenant's conversion queue or be able to delete/retry another tenant's jobs. A failed ownership check must not reveal whether a foreign job identifier exists.

### Follow-up gaps

- Decide explicitly, in product/architecture authority, whether a future platform-wide operator role is required. Do not infer global scope from the word `admin`; introduce a separate capability and audited cross-tenant use case if product requirements demand it.
- Move tenant filtering into a repository/application-service query when persistent storage replaces or supplements the current global `getAllJobs()` path, so tenant isolation is enforced before materializing unrelated aggregates.
- Keep administrative audit records tied to verified subject and tenant claims.
- Verify the final exact PR head against repository tests and security gates before Ready/merge.
