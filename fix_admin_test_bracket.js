const fs = require('fs');

const path = 'src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java';
let content = fs.readFileSync(path, 'utf8');

content = content.replace('    @BeforeEach\n    void setUp() {\n        conversionService = mock(DocumentConversionService.class);\n        tenantAccessService = new TenantAccessService();\n        controller = new AdminController(conversionService, tenantAccessService);\n        webTestClient = WebTestClient.bindToController(controller)\n                .controllerAdvice(new ApiExceptionHandler())\n                .build();', '    @BeforeEach\n    void setUp() {\n        conversionService = mock(DocumentConversionService.class);\n        tenantAccessService = new TenantAccessService();\n        controller = new AdminController(conversionService, tenantAccessService);\n        webTestClient = WebTestClient.bindToController(controller)\n                .controllerAdvice(new ApiExceptionHandler())\n                .build();\n    }');

fs.writeFileSync(path, content);
