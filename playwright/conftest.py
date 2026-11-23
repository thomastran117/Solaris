import os

import pytest
from playwright.async_api import async_playwright

# The Docker Compose stack and the CI smoke job serve the frontend on port 3000.
# For `npm run dev`, use BASE_URL=http://localhost:3090.
BASE_URL = os.getenv("BASE_URL", "http://localhost:3000").rstrip("/")


@pytest.fixture(scope="session")
def base_url() -> str:
    return BASE_URL


@pytest.fixture(scope="session")
async def playwright_instance():
    async with async_playwright() as p:
        yield p


@pytest.fixture(scope="session")
async def browser(playwright_instance):
    browser = await playwright_instance.chromium.launch(headless=True)
    yield browser
    await browser.close()


@pytest.fixture
async def context(browser):
    context = await browser.new_context()
    yield context
    await context.close()


@pytest.fixture
async def no_js_context(browser):
    """A browser that never runs JavaScript: only server-rendered HTML is visible."""
    context = await browser.new_context(java_script_enabled=False)
    yield context
    await context.close()


@pytest.fixture
async def page(context):
    page = await context.new_page()
    page.set_default_navigation_timeout(15_000)
    page.set_default_timeout(15_000)
    yield page
    await page.close()


@pytest.fixture
async def goto(page):
    async def _goto(path: str = "/"):
        return await page.goto(f"{BASE_URL}{path}", wait_until="domcontentloaded")

    return _goto
