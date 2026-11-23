"""Fixture stand-in for the Spring Boot API, used by the frontend smoke tests in CI.

It serves the public storefront endpoints that server-rendered pages read, and a
minimal session flow (refresh, login) for the auth-boundary tests, all wrapped in
the backend's ApiResponse envelope. It is intentionally tiny and dependency-free
(Python standard library only) so CI can exercise the real Next.js standalone
server without PostgreSQL, Redis, Elasticsearch, or Kafka.

Run:  python playwright/stub_api.py [port]   (default 8090)

Every request is appended to stub_api_requests.log (next to this file) so tests can
assert, for example, that server-side rendering never forwards credentials.
"""

from __future__ import annotations

import base64
import json
import re
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

LOG = Path(__file__).with_name("stub_api_requests.log")
# ThreadingHTTPServer handles requests concurrently; serialise log appends so
# each request stays on its own line.
LOG_LOCK = threading.Lock()

MARKETPLACE_ID = "11111111-1111-1111-1111-111111111111"
COMPANY_ID = "22222222-2222-2222-2222-222222222222"
PRODUCT_ID = "33333333-3333-3333-3333-333333333333"
KIT_ID = "55555555-5555-5555-5555-555555555555"
NOW = "2026-11-01T12:00:00Z"

PRODUCT = {
    "id": PRODUCT_ID, "companyId": COMPANY_ID, "marketplaceId": MARKETPLACE_ID,
    "vendorId": None, "vendorName": "Aurora Audio", "vendorTier": None,
    "name": "Aurora Wireless Headphones",
    # Contains markup on purpose: JSON-LD output must escape it.
    "description": "Noise-cancelling over-ear headphones with a 40-hour battery </script><script>alert(1)</script>",
    "sku": "AUR-1", "price": 199.99, "compareAtPrice": 249.99, "currency": "usd",
    "category": "Audio", "brand": "Aurora", "tags": None,
    "thumbnailUrl": "https://example.test/aurora.jpg", "images": [], "variants": [],
    "stock": 12, "status": "ACTIVE", "scheduledPublishAt": None, "publishedAt": NOW,
    "featured": True, "purchasable": True, "preorderEnabled": False,
    "preorderExpectedDate": None, "createdAt": NOW, "updatedAt": NOW,
    "activePromotion": None,
}
FACETS = {"categories": [{"value": "Audio", "count": 1}], "brands": [{"value": "Aurora", "count": 1}], "priceRanges": []}
COLLECTION = {
    "id": "c1", "companyId": None, "name": "Winter Sound Picks", "slug": "winter-sound",
    "description": "Our favourite audio gear for the season.", "imageUrl": None,
    "type": "MANUAL", "status": "ACTIVE", "featured": True, "featuredRank": 1,
    "rulesJson": None, "productCount": 1, "lastMaterialisedAt": None,
    "createdAt": NOW, "updatedAt": NOW,
}
COMPANY = {
    "id": COMPANY_ID, "name": "Aurora Audio Co", "city": "Toronto", "country": "CA",
    "logoUrl": None, "website": None, "description": "Independent headphone maker.",
    "industry": "Audio", "foundedYear": 2019,
}
KIT = {
    "id": KIT_ID, "companyId": COMPANY_ID, "name": "Studio Starter Kit",
    "description": "Everything for a home studio.", "thumbnailUrl": None,
    "computedMinPrice": 299, "computedMaxPrice": 499, "compareAtPrice": None,
    "currency": "USD", "status": "ACTIVE", "listed": True, "purchasable": True,
    "slots": [], "createdAt": NOW, "updatedAt": NOW,
}
SAVED_LIST = {
    "id": "l1", "ownerDisplayName": "Sam", "name": "Sam's Wishlist", "type": "WISHLIST",
    "description": "Gift ideas", "shareSlug": "sam-wish", "items": [],
    "createdAt": NOW, "updatedAt": NOW,
}
ANNOUNCEMENTS = {
    "items": [{
        "id": "a1", "companyId": COMPANY_ID, "companyName": COMPANY["name"], "companyLogoUrl": None,
        "title": "Holiday sale is live", "body": "20% off all headphones.", "type": "SALE",
        "status": "PUBLISHED", "productId": None, "publishedAt": "2026-11-20T23:30:00Z",
        "createdAt": NOW, "updatedAt": NOW,
    }],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": False, "hasPrevious": False,
}


def _jwt() -> str:
    payload = base64.b64encode(json.dumps({"sub": "u1", "exp": 4102444800}).encode()).decode().rstrip("=")
    return f"eyJhbGciOiJIUzI1NiJ9.{payload}.sig"


SESSION_TOKEN = _jwt()


def paged(items, size=24, **extra):
    return {"items": items, "page": 0, "size": size, "totalElements": len(items), "totalPages": 1,
            "hasNext": False, "hasPrevious": False, **extra}


def ok(data):
    return 200, {"success": True, "message": "ok", "data": data, "error": None, "meta": None}


def err(status, code):
    return status, {"success": False, "message": code, "data": None, "error": {"code": code}, "meta": None}


MP = r"[^/]+"
ROUTES = [
    (rf"^/api/marketplaces/{MP}/collections/featured$", lambda: ok([COLLECTION])),
    (rf"^/api/marketplaces/{MP}/collections/winter-sound/products$",
     lambda: ok(paged([{"productId": PRODUCT_ID, "pinnedRank": 1}], size=50))),
    (rf"^/api/marketplaces/{MP}/collections/winter-sound$", lambda: ok(COLLECTION)),
    (rf"^/api/marketplaces/{MP}/catalog/products/{PRODUCT_ID}$", lambda: ok(PRODUCT)),
    (rf"^/api/marketplaces/{MP}/catalog/products$", lambda: ok(paged([PRODUCT], facets=FACETS))),
    (rf"^/api/companies/{COMPANY_ID}/public$", lambda: ok(COMPANY)),
    (rf"^/api/companies/{MP}/catalog/search$", lambda: ok(paged([PRODUCT], facets=FACETS))),
    (rf"^/api/companies/{MP}/announcements$", lambda: ok(ANNOUNCEMENTS)),
    (rf"^/api/companies/{COMPANY_ID}/kits/{KIT_ID}$", lambda: ok(KIT)),
    (r"^/api/saved-lists/public/sam-wish$", lambda: ok(SAVED_LIST)),
]


class Handler(BaseHTTPRequestHandler):
    server_version = "ShopWaveStub/1.0"

    def log_message(self, fmt, *args):  # keep CI output quiet; requests go to LOG
        pass

    def _send(self, status, body, headers=None):
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(raw)

    def _record(self, path, body=None):
        with LOG_LOCK, LOG.open("a", encoding="utf-8") as f:
            f.write(json.dumps({
                "method": self.command, "path": path,
                "cookie": self.headers.get("Cookie"), "authorization": self.headers.get("Authorization"),
                "body": body,
            }) + "\n")

    def _read_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            return json.loads(raw) if raw else None
        except ValueError:
            return None

    def _route(self):
        parsed = urlparse(self.path)
        path = parsed.path
        query = parse_qs(parsed.query)
        body = self._read_json() if self.command in ("POST", "PATCH", "PUT") else None
        # Auth bodies are recorded so tests can assert the request shape; the stub
        # only ever sees fixture credentials.
        self._record(self.path, body if path.startswith("/api/auth/") else None)
        cookie = self.headers.get("Cookie") or ""
        if path == "/api/auth/refresh":
            if "refreshToken=valid" in cookie:
                return self._send(*ok({"accessToken": SESSION_TOKEN, "email": "sam@example.test", "role": "USER", "tier": "FREE"}),
                                  {"Set-Cookie": "refreshToken=valid; Path=/; HttpOnly; SameSite=Strict"})
            return self._send(*err(401, "UNAUTHORIZED"))
        if path in ("/api/auth/login", "/api/auth/verify-device") and self.command == "POST":
            if path == "/api/auth/verify-device" and query.get("token") != ["good"]:
                return self._send(*err(400, "This device link has expired."))
            return self._send(*ok({"token": SESSION_TOKEN, "email": "sam@example.test", "usertype": "USER", "tier": "FREE"}),
                              {"Set-Cookie": "refreshToken=valid; Path=/; HttpOnly; SameSite=Strict"})
        if path == "/api/auth/signup" and self.command == "POST":
            return self._send(201, ok({"message": "Verification email sent"})[1])
        if path == "/api/auth/verify" and self.command == "POST":
            if query.get("token") == ["good"]:
                return self._send(*ok({"message": "Email verified successfully."}))
            return self._send(*err(400, "This verification link has expired."))
        if path == "/api/health":
            return self._send(*ok({"status": "UP"}))
        for pattern, handler in ROUTES:
            if re.match(pattern, path):
                return self._send(*handler())
        return self._send(*err(404, "NOT_FOUND"))

    do_GET = _route
    do_POST = _route


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
    LOG.write_text("", encoding="utf-8")
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"stub API listening on http://127.0.0.1:{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
