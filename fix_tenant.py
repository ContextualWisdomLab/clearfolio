file_path = "src/main/java/com/clearfolio/viewer/auth/TenantAccessService.java"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace(
    'public static String signClaims(TenantContext context, String issuedAt, String secret) {',
    'public static String signClaims(\n            final TenantContext context,\n            final String issuedAt,\n            final String secret) {'
)

with open(file_path, "w") as f:
    f.write(content)
