const fs = require('fs');

const path = 'src/main/java/com/clearfolio/viewer/auth/TenantAccessService.java';
let content = fs.readFileSync(path, 'utf8');

const targetSignClaims = `    public static String signClaims(TenantContext context, String issuedAt, String secret) {
        String payload = String.join("\\n",
                context.tenantId().length() + ":" + context.tenantId(),
                context.subjectId().length() + ":" + context.subjectId(),
                context.canonicalPermissions().length() + ":" + context.canonicalPermissions(),
                issuedAt.length() + ":" + issuedAt
        );
        return hmac(payload, secret);
    }`;

const newSignClaims = `    public static String signClaims(TenantContext context, String issuedAt, String secret) {
        String payload = String.join("\\n",
                context.tenantId(),
                context.subjectId(),
                context.canonicalPermissions(),
                issuedAt
        );
        return hmac(payload, secret);
    }`;

content = content.replace(targetSignClaims, newSignClaims);
fs.writeFileSync(path, content);
