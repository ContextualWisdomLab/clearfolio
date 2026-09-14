with open("src/main/java/com/clearfolio/viewer/auth/TenantPermissions.java", "r") as f:
    content = f.read()
if "public static final String AUDIT_READ" in content:
    content = content.replace("public static final String AUDIT_READ", "/**\n     * Permission required to perform admin read actions.\n     */\n    public static final String ADMIN_READ = \"admin:read\";\n\n    /**\n     * Permission required to perform admin write actions.\n     */\n    public static final String ADMIN_WRITE = \"admin:write\";\n\n    /**\n     * Permission required to read audit evidence.\n     */\n    public static final String AUDIT_READ")
with open("src/main/java/com/clearfolio/viewer/auth/TenantPermissions.java", "w") as f:
    f.write(content)
