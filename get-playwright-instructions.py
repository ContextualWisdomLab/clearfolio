import urllib.request
import json
url = "http://localhost:8080/assets/viewer/demo-fixtures.json"
try:
    req = urllib.request.Request(url)
    response = urllib.request.urlopen(req)
    print("Demo server is running.")
except Exception as e:
    print(f"Error checking demo server: {e}")
