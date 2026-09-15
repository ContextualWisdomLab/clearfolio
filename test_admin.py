import urllib.request
import urllib.error

try:
    # Use headers that trigger the TenantAccessService if it was configured
    headers = {
        'X-Clearfolio-Tenant-Id': 'tenant',
        'X-Clearfolio-Subject-Id': 'user',
        'X-Clearfolio-Permissions': 'job:read'
    }
    req = urllib.request.Request('http://localhost:8080/api/v1/admin/convert/jobs', headers=headers)
    response = urllib.request.urlopen(req)
    print(f"Status: {response.getcode()}")
    print(f"Content: {response.read().decode()}")
except urllib.error.URLError as e:
    if hasattr(e, 'code'):
        print(f"HTTP Error: {e.code}")
    else:
        print(f"Connection Error: {e.reason}")
