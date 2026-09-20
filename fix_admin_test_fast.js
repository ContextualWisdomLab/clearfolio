const fs = require('fs');

const path = 'src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java';
let content = fs.readFileSync(path, 'utf8');

// remove duplicate headers
content = content.replace(/\.header\("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant"\)\n                \.header\("X-Clearfolio-Subject-Id", "admin"\)\n                \.header\("X-Clearfolio-Permissions", "admin:(read|write)"\)\n                \.header\("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant"\)\n                \.header\("X-Clearfolio-Subject-Id", "admin"\)\n                \.header\("X-Clearfolio-Permissions", "admin:(read|write)"\)\n                \.header\("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant"\)\n                \.header\("X-Clearfolio-Subject-Id", "admin"\)\n                \.header\("X-Clearfolio-Permissions", "admin:(read|write)"\)\n                \.header\("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant"\)\n                \.header\("X-Clearfolio-Subject-Id", "admin"\)\n                \.header\("X-Clearfolio-Permissions", "admin:(read|write)"\)/g, '.header("X-Clearfolio-Tenant-Id", "clearfolio-demo-tenant")\n                .header("X-Clearfolio-Subject-Id", "admin")\n                .header("X-Clearfolio-Permissions", "admin:$1")');

fs.writeFileSync(path, content);
