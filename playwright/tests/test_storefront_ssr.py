"""Server-rendered storefront (#84): content, metadata and status codes without JavaScript.

Requires the frontend to be built and running against playwright/stub_api.py with
DEFAULT_MARKETPLACE_ID set to stub_api.MARKETPLACE_ID (the CI smoke job does this).
"""

import json
import re
import uuid

import pytest
from playwright.async_api import expect

from stub_api import COMPANY_ID, KIT_ID, LOG, PRODUCT_ID

PAGES = [
    ("/", "Winter Sound Picks"),
    ("/browse", "Aurora Wireless Headphones"),
    (f"/products/{PRODUCT_ID}", "Aurora Wireless Headphones"),
    (f"/c/{COMPANY_ID}", "Aurora Audio Co"),
    ("/collections/winter-sound", "Winter Sound Picks"),
    (f"/kits/{COMPANY_ID}/{KIT_ID}", "Studio Starter Kit"),
    ("/lists/public/sam-wish", "Sam's Wishlist"),
]

OPACITY = """el => { let o = 1; for (let n = el; n && n.nodeType === 1; n = n.parentElement)
                     { o *= parseFloat(getComputedStyle(n).opacity || '1'); } return o; }"""


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize("path,text", PAGES)
async def test_content_is_visible_without_javascript(no_js_context, base_url, path, text):
    page = await no_js_context.new_page()
    response = await page.goto(base_url + path)
    assert response is not None and response.status == 200
    element = page.get_by_text(text, exact=False).first
    await expect(element).to_be_attached()
    # Entrance animations must not leave server HTML invisible (opacity:0).
    assert await element.evaluate(OPACITY) > 0.99


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize("path,text", PAGES)
async def test_pages_hydrate_without_errors(context, base_url, path, text):
    page = await context.new_page()
    problems = []
    page.on("pageerror", lambda e: problems.append(f"pageerror: {e}"))
    page.on(
        "console",
        lambda m: problems.append(f"console.{m.type}: {m.text}")
        if m.type == "error" and "401" not in m.text and "Failed to load resource" not in m.text
        else None,
    )
    await page.goto(base_url + path, wait_until="networkidle")
    await expect(page.get_by_text(text, exact=False).first).to_be_visible()
    await page.wait_for_timeout(500)
    assert problems == []


@pytest.mark.asyncio(loop_scope="session")
async def test_product_metadata_and_json_ld(context, base_url):
    response = await context.request.get(f"{base_url}/products/{PRODUCT_ID}")
    html = await response.text()
    assert "<title>Aurora Wireless Headphones | ShopWave</title>" in html
    assert re.search(rf'<link rel="canonical" href="[^"]*/products/{PRODUCT_ID}"', html)
    assert '<meta property="og:image" content="https://example.test/aurora.jpg"' in html
    ld = re.search(r'<script type="application/ld\+json">(.*?)</script>', html, re.S)
    assert ld, "Product JSON-LD missing"
    # The product description contains </script>; it must be escaped, not raw.
    assert "</script><script>alert(1)" not in html
    data = json.loads(ld.group(1))
    assert data["@type"] == "Product" and data["offers"]["priceCurrency"] == "USD"


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize(
    "path",
    [
        "/products/44444444-4444-4444-4444-444444444444",
        "/products/not-a-uuid",
        "/c/44444444-4444-4444-4444-444444444444",
        "/collections/missing",
        f"/kits/{COMPANY_ID}/44444444-4444-4444-4444-444444444444",
        "/lists/public/missing",
    ],
)
async def test_missing_entities_return_404(context, base_url, path):
    response = await context.request.get(base_url + path)
    assert response.status == 404


@pytest.mark.asyncio(loop_scope="session")
async def test_filtered_listings_are_noindex(context, base_url):
    html = await (await context.request.get(f"{base_url}/browse?q=aurora")).text()
    assert '<meta name="robots" content="noindex, follow"/>' in html
    html = await (await context.request.get(f"{base_url}/browse?category=Audio&sort=price&page=2")).text()
    assert re.search(r'<link rel="canonical" href="[^"]*/browse\?category=Audio&amp;page=2"', html)


@pytest.mark.asyncio(loop_scope="session")
async def test_server_rendering_never_forwards_credentials(context, base_url):
    """SSR reads must be anonymous even when the visitor sends a session cookie."""
    unique = uuid.uuid4().hex  # unique query -> uncached server fetch
    before = len(LOG.read_text(encoding="utf-8").splitlines())
    response = await context.request.get(
        f"{base_url}/browse?q={unique}", headers={"Cookie": "refreshToken=valid"}
    )
    assert response.status == 200
    new = [json.loads(line) for line in LOG.read_text(encoding="utf-8").splitlines()[before:]]
    ssr_calls = [r for r in new if unique in r["path"]]
    assert ssr_calls, "expected the server render to query the catalog"
    assert all(r["cookie"] is None and r["authorization"] is None for r in ssr_calls)
