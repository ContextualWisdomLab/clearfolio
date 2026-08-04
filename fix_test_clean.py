import re

with open("src/test/java/com/clearfolio/viewer/controller/ConversionControllerTest.java", "r") as f:
    content = f.read()

# We have duplicated .headers(h -> h.setAll(DEMO_HEADERS))
# Let's remove them and then do one clean pass.

# Remove all occurrences of "\n                .headers(h -> h.setAll(DEMO_HEADERS))"
content = content.replace('\n                .headers(h -> h.setAll(DEMO_HEADERS))', '')

# Now re-apply them correctly but ONLY for the download methods that existed *before* our custom ones.
# Actually, the original webClient.get().uri() has no headers. So we need to put it on all downloadArtifact calls
# except downloadArtifactRequiresJobReadPermission and downloadArtifactRequiresHeaders

# Wait, let's just use string replacement on the exact tests that were there before:
def clean_download(m):
    body = m.group(0)
    if "downloadArtifactRequires" in body or "CrossTenantAccess" in body:
        return body
    body = re.sub(r'(\.uri\("/api/v1/convert/jobs/\{jobId\}/download", jobId\))', r'\1\n                .headers(h -> h.setAll(DEMO_HEADERS))', body)
    return body

content = re.sub(r'(void downloadArtifact[A-Za-z0-9_]+\(\) \{[\s\S]*?(?=\n    @Test|\n\}))', clean_download, content)

with open("src/test/java/com/clearfolio/viewer/controller/ConversionControllerTest.java", "w") as f:
    f.write(content)
