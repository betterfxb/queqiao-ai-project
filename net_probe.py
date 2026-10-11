import urllib.request
import traceback

for url in ["http://127.0.0.1:8080/api/health", "http://localhost:8080/api/health"]:
    try:
        r = urllib.request.urlopen(url, timeout=5)
        print("OK", url, r.read().decode()[:120])
    except Exception:
        print("FAIL", url)
        traceback.print_exc()
