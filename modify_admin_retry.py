import sys

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "r") as f:
    content = f.read()

search = """        RetryDeadLetterResult result = conversionService.retryDeadLettered(
                jobId,
                "admin"
        );"""

replace = """        RetryDeadLetterResult result = conversionService.retryDeadLettered(
                jobId,
                tenantContext.subjectId()
        );"""

content = content.replace(search, replace)

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "w") as f:
    f.write(content)
