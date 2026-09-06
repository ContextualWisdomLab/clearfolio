with open("src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java", "r") as f:
    content = f.read()

content = content.replace(
"""import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import java.util.Arrays;""",
"""import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import java.util.Arrays;
import com.clearfolio.viewer.auth.TenantAccessService;""")

with open("src/test/java/com/clearfolio/viewer/controller/AdminControllerTest.java", "w") as f:
    f.write(content)
