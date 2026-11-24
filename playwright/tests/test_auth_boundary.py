"""Auth boundary (#85): proxy redirect, deep-link return, open-redirect protection.

The stub API restores a session only for the cookie value `refreshToken=valid` and
accepts any login, setting that cookie.
"""

import pytest

DEEP_LINK = "/orders/abc?x=1"
ENCODED = "%2Forders%2Fabc%3Fx%3D1"


@pytest.mark.asyncio(loop_scope="session")
async def test_proxy_redirects_anonymous_deep_links_with_next(context, base_url):
    response = await context.request.get(base_url + DEEP_LINK, max_redirects=0)
    assert response.status == 307
    assert response.headers["location"] == f"/login?next={ENCODED}"


@pytest.mark.asyncio(loop_scope="session")
async def test_protected_html_contains_no_private_data(context, base_url):
    response = await context.request.get(
        f"{base_url}/admin/products", headers={"Cookie": "refreshToken=valid"}, max_redirects=0
    )
    assert response.status == 200
    html = await response.text()
    assert "Loading your session" in html
    assert "sam@example.test" not in html and "eyJhbGci" not in html


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize("path", ["/lists/public/sam-wish", "/browse", "/login"])
async def test_public_paths_are_not_redirected(context, base_url, path):
    response = await context.request.get(base_url + path, max_redirects=0)
    assert response.status == 200


@pytest.mark.asyncio(loop_scope="session")
async def test_login_returns_to_the_deep_link(page, base_url):
    await page.goto(base_url + DEEP_LINK)
    await page.wait_for_url(f"**/login?next={ENCODED}")
    await page.locator("input[name=email]").fill("sam@example.test")
    await page.locator("input[name=password]").fill("correct horse battery")
    await page.locator("form button[type=submit]").first.click()
    await page.wait_for_url(f"{base_url}{DEEP_LINK}")


@pytest.mark.asyncio(loop_scope="session")
async def test_restored_session_bounces_to_next(context, page, base_url):
    """Stripe/email return: the cookie exists but a cross-site navigation dropped it."""
    await context.add_cookies([{"name": "refreshToken", "value": "valid", "url": base_url}])
    await page.goto(f"{base_url}/login?next=%2Faccount%3Fupgrade%3Dsuccess")
    await page.wait_for_url(f"{base_url}/account?upgrade=success")


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize("evil", ["%2F%2Fevil.test%2Fphish", "https%3A%2F%2Fevil.test", "%2F%5Cevil.test"])
async def test_unsafe_next_never_leaves_the_site(context, page, base_url, evil):
    await context.add_cookies([{"name": "refreshToken", "value": "valid", "url": base_url}])
    await page.goto(f"{base_url}/login?next={evil}")
    # Unsafe targets fall back to DEFAULT_AFTER_LOGIN, the storefront home.
    await page.wait_for_url(f"{base_url}/")


@pytest.mark.asyncio(loop_scope="session")
async def test_revoked_cookie_is_caught_by_the_client_guard(context, page, base_url):
    await context.add_cookies([{"name": "refreshToken", "value": "revoked", "url": base_url}])
    await page.goto(f"{base_url}/admin/products?tab=2")
    await page.wait_for_url(f"{base_url}/login?next=%2Fadmin%2Fproducts%3Ftab%3D2")
