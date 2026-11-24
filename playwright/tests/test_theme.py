"""Light/dark theming: no flash of the wrong theme, persistence, forced-dark routes.

The inline script in <head> (frontend/src/lib/theme/themeScript.ts) must set the
`dark` class before first paint, so every check reads <html> at
`domcontentloaded`, before React hydrates.
"""

import pytest

IS_DARK = "() => document.documentElement.classList.contains('dark')"
SET_LIGHT = "try { localStorage.setItem('shopwave-theme', 'light') } catch (e) {}"


async def _is_dark(page) -> bool:
    return await page.evaluate(IS_DARK)


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize("scheme,expected", [("dark", True), ("light", False)])
async def test_follows_the_system_preference_before_hydration(browser, base_url, scheme, expected):
    context = await browser.new_context(color_scheme=scheme)
    page = await context.new_page()
    await page.goto(f"{base_url}/", wait_until="domcontentloaded")
    assert await _is_dark(page) is expected
    assert await page.evaluate("() => document.documentElement.style.colorScheme") == scheme
    await context.close()


@pytest.mark.asyncio(loop_scope="session")
async def test_stored_choice_overrides_the_system(browser, base_url):
    context = await browser.new_context(color_scheme="dark")
    await context.add_init_script(SET_LIGHT)
    page = await context.new_page()
    await page.goto(f"{base_url}/browse", wait_until="domcontentloaded")
    assert await _is_dark(page) is False
    await context.close()


@pytest.mark.asyncio(loop_scope="session")
async def test_toggle_switches_and_persists(browser, base_url):
    context = await browser.new_context(color_scheme="light")
    page = await context.new_page()
    await page.goto(f"{base_url}/")
    await page.get_by_role("button", name="Change theme").click()
    await page.get_by_role("menuitemradio", name="Dark").click()
    assert await _is_dark(page) is True

    await page.reload(wait_until="domcontentloaded")
    assert await _is_dark(page) is True
    assert await page.evaluate("() => localStorage.getItem('shopwave-theme')") == "dark"
    await context.close()


@pytest.mark.asyncio(loop_scope="session")
async def test_unmigrated_routes_stay_dark(browser, base_url):
    """Account and seller views still use the legacy palette and are pinned dark."""
    context = await browser.new_context(color_scheme="light")
    await context.add_init_script(SET_LIGHT)
    await context.add_cookies([{"name": "refreshToken", "value": "valid", "url": base_url}])
    page = await context.new_page()
    await page.goto(f"{base_url}/admin/products", wait_until="domcontentloaded")
    assert await _is_dark(page) is True
    await context.close()


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize("path", ["/orders", "/account", "/lists"])
async def test_account_routes_follow_the_visitor_theme(browser, base_url, path):
    context = await browser.new_context(color_scheme="dark")
    await context.add_init_script(SET_LIGHT)
    await context.add_cookies([{"name": "refreshToken", "value": "valid", "url": base_url}])
    page = await context.new_page()
    await page.goto(f"{base_url}{path}", wait_until="domcontentloaded")
    assert await _is_dark(page) is False
    await context.close()


@pytest.mark.asyncio(loop_scope="session")
async def test_shared_public_lists_are_not_forced_dark(browser, base_url):
    context = await browser.new_context(color_scheme="light")
    page = await context.new_page()
    await page.goto(f"{base_url}/lists/public/sam-wish", wait_until="domcontentloaded")
    assert await _is_dark(page) is False
    await context.close()


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize("scheme", ["light", "dark"])
@pytest.mark.parametrize("path", ["/", "/browse", "/login", "/signup"])
async def test_pages_hydrate_cleanly_in_both_themes(browser, base_url, scheme, path):
    context = await browser.new_context(color_scheme=scheme)
    page = await context.new_page()
    problems: list[str] = []
    # The anonymous session restore answers 401; Chromium logs that as a failed
    # resource, which test_storefront_ssr ignores for the same reason.
    page.on(
        "console",
        lambda m: problems.append(f"console.{m.type}: {m.text}")
        if m.type == "error" and "401" not in m.text and "Failed to load resource" not in m.text
        else None,
    )
    page.on("pageerror", lambda e: problems.append(f"pageerror: {e}"))
    await page.goto(f"{base_url}{path}", wait_until="networkidle")
    assert problems == []
    await context.close()
