import pytest
from playwright.async_api import expect


@pytest.mark.asyncio(loop_scope="session")
async def test_homepage(goto, page, base_url):
    response = await goto("/")
    assert response is not None and response.status == 200
    await expect(page).to_have_url(f"{base_url}/")
    await expect(page).to_have_title("ShopWave")


@pytest.mark.asyncio(loop_scope="session")
async def test_healthz(context, base_url):
    response = await context.request.get(f"{base_url}/healthz")
    assert response.status == 200
    assert await response.json() == {"status": "ok"}
    assert response.headers.get("cache-control") == "no-store"


@pytest.mark.asyncio(loop_scope="session")
async def test_unknown_route_is_a_real_404(context, base_url):
    response = await context.request.get(f"{base_url}/no-such-page")
    assert response.status == 404
    assert "Page not found" in await response.text()
