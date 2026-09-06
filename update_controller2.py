with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "r") as f:
    content = f.read()

content = content.replace(
"""     * @param deadLettered optional filter for dead-lettered jobs""",
"""     * @param headers request headers
     * @param deadLettered optional filter for dead-lettered jobs""")
content = content.replace(
"""     * @param jobId conversion job identifier
     * @return no content on success""",
"""     * @param headers request headers
     * @param jobId conversion job identifier
     * @return no content on success""")
content = content.replace(
"""     * @param jobId conversion job identifier
     * @return accepted response on success""",
"""     * @param headers request headers
     * @param jobId conversion job identifier
     * @return accepted response on success""")

with open("src/main/java/com/clearfolio/viewer/controller/AdminController.java", "w") as f:
    f.write(content)
