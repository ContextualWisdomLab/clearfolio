const fs = require('fs');

const path = 'src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java';
let content = fs.readFileSync(path, 'utf8');

// The original test didn't have TenantAccessService logic.
// Now it requires `X-Clearfolio-Permissions` in headers to succeed.
// We should also add negative tests to check unauthorized access.
// Since it's easier, let me first reset and then redo correctly.
