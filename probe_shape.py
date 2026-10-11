# -*- coding: utf-8 -*-
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
UA = {"User-Agent": "x", "Content-Type": "application/json"}


def req(method, path, token=None, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    h = dict(UA)
    if token:
        h["Authorization"] = "Bearer " + token
    r = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
    try:
        resp = urllib.request.urlopen(r, timeout=20)
        raw = resp.read()
        return resp.status, json.loads(raw.decode("utf-8")) if raw else None
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read() or b"null")
        except Exception:
            return e.code, None


st, b = req("POST", "/api/auth/login", body={"username": "admin", "password": "123456"})
tok = b["token"]

st, mb = req("GET", "/api/members?page=1&size=2", token=tok)
print("members keys:", list(mb.keys()) if isinstance(mb, dict) else type(mb))

st, pb = req("GET", "/api/pairs?page=1&size=200", token=tok)
print("pairs keys:", list(pb.keys()) if isinstance(pb, dict) else type(pb))
rows = pb.get("list") or pb.get("rows") or [] if isinstance(pb, dict) else pb
print("pairs count:", len(rows))
from collections import Counter
print("status dist:", Counter([r.get("status") for r in rows]))
if rows:
    print("sample pair keys:", list(rows[0].keys()))
    print("sample:", json.dumps(rows[0], ensure_ascii=False)[:300])
