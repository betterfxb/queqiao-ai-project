import time
import urllib.request

for i in range(90):
    try:
        r = urllib.request.urlopen("http://localhost:8080/api/health", timeout=3)
        print("backend UP after %ds -> %s" % (i * 2, r.read().decode()[:200]))
        break
    except Exception:
        time.sleep(2)
else:
    print("backend NOT UP")
