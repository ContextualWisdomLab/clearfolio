package com.clearfolio.viewer.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TenantPermissionContractTest {

    @Test
    void tenantAdminRoleCarriesAdminOperatePermission() throws IOException {
        String contract = Files.readString(
                Path.of("docs/security/2026-07-02-auth-tenant-model.md"));
        String tenantAdminRow = contract.lines()
                .filter(line -> line.startsWith("| `tenant_admin` |"))
                .findFirst()
                .orElseThrow();

        assertTrue(
                tenantAdminRow.contains("`" + TenantPermissions.ADMIN_OPERATE + "`"),
                "tenant_admin must receive the permission enforced by AdminController");
    }
}
