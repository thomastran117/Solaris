"""Cart and checkout against playwright/stub_api.py (payments unconfigured in CI)."""

import json
import re

import pytest
from playwright.async_api import expect

from stub_api import LOG, MARKETPLACE_ID, COMPANY_ID, PRODUCT_ID

CART = [{
    "kind": "product", "lineId": f"p:{PRODUCT_ID}:-", "productId": PRODUCT_ID,
    "variantId": None, "variantLabel": None, "name": "Aurora Wireless Headphones",
    "thumbnailUrl": None, "price": 199.99, "currency": "USD", "quantity": 1,
    "marketplaceId": MARKETPLACE_ID, "companyId": COMPANY_ID,
}]
SEED_CART = f"localStorage.setItem('shopwave.cart.v1', {json.dumps(json.dumps(CART))})"


def _logged(path: str) -> list[dict]:
    entries = [json.loads(line) for line in LOG.read_text(encoding="utf-8").splitlines() if line]
    return [e for e in entries if e["path"].split("?")[0] == path]


@pytest.mark.asyncio(loop_scope="session")
async def test_add_to_cart_opens_the_drawer_and_persists(page, base_url):
    await page.goto(f"{base_url}/products/{PRODUCT_ID}")
    await page.get_by_role("button", name="Add to cart").click()

    drawer = page.get_by_role("dialog", name=re.compile(r"Your cart \(1\)"))
    await expect(drawer).to_be_visible()
    await expect(drawer.get_by_text("Aurora Wireless Headphones")).to_be_visible()
    await expect(drawer.get_by_role("link", name="Checkout")).to_have_attribute(
        "href", "/checkout?currency=USD"
    )
    await page.keyboard.press("Escape")
    await expect(page.get_by_role("button", name="Cart, 1 item")).to_be_visible()

    await page.goto(f"{base_url}/cart")
    await expect(page.get_by_role("heading", name=re.compile("Your cart"))).to_be_visible()
    await page.reload()
    await expect(page.get_by_text("Aurora Wireless Headphones").first).to_be_visible()


@pytest.mark.asyncio(loop_scope="session")
async def test_checkout_requires_a_session(context, base_url):
    page = await context.new_page()
    await page.goto(f"{base_url}/checkout?currency=USD")
    await page.wait_for_url(f"{base_url}/login?next=%2Fcheckout%3Fcurrency%3DUSD")


@pytest.mark.asyncio(loop_scope="session")
async def test_checkout_places_the_order_with_an_idempotency_key(context, base_url):
    await context.add_cookies([{"name": "refreshToken", "value": "valid", "url": base_url}])
    await context.add_init_script(SEED_CART)
    page = await context.new_page()
    await page.goto(f"{base_url}/checkout?currency=USD")

    await page.get_by_label("Full name").fill("Sam Shopper")
    await page.get_by_label("Street address").fill("1 Main St")
    await page.get_by_label("City").fill("Springfield")
    await page.get_by_label("State / province").fill("IL")
    await page.get_by_label("Postal code").fill("62701")
    await page.get_by_role("button", name="Continue to payment").click()

    await page.wait_for_url(re.compile(r"/checkout\?order="))
    await expect(page.get_by_text("Your items are reserved")).to_be_visible()
    # CI has no Stripe key, so the payment step says so instead of failing.
    await expect(page.get_by_text("Payments aren't configured")).to_be_visible()

    request = _logged("/api/orders")[-1]
    assert re.fullmatch(r"[0-9a-f-]{36}", request["idempotencyKey"] or "")
    assert request["body"]["shipCountry"] == "US"
    assert request["body"]["items"] == [{"productId": PRODUCT_ID, "variantId": None, "quantity": 1}]
