with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "r") as f:
    content = f.read()

content = content.replace(
"""    public AdminJobListResponse getAllJobs(
            @RequestHeader final HttpHeaders headers,""",
"""    public AdminJobListResponse getAllJobs(
            @RequestHeader final HttpHeaders headers,""")

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "w") as f:
    f.write(content)
