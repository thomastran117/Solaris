"""Account creation and emailed verification links, against playwright/stub_api.py."""

import json

import pytest
from playwright.async_api import expect

from stub_api import LOG


def _logged(path: str) -> list[dict]:
    entries = [json.loads(line) for line in LOG.read_text(encoding="utf-8").splitlines() if line]
    return [e for e in entries if e["path"].split("?")[0] == path]


@pytest.mark.asyncio(loop_scope="session")
async def test_signup_sends_captcha_and_shows_the_inbox_step(page, base_url):
    await page.goto(f"{base_url}/signup")
    await page.get_by_label("Email").fill("new@example.test")
    await page.get_by_label("Password", exact=True).fill("Str0ng!pass")
    await page.get_by_label("Confirm password").fill("Str0ng!pass")
    await page.get_by_role("checkbox").check()
    await page.get_by_role("button", name="Create account").click()

    await expect(page.get_by_role("heading", name="Check your inbox")).to_be_visible()
    body = _logged("/api/auth/signup")[-1]["body"]
    assert body["email"] == "new@example.test"
    # The backend rejects requests without the field; it is empty without a site key.
    assert "captcha" in body


@pytest.mark.asyncio(loop_scope="session")
async def test_login_links_to_signup_keeping_next(page, base_url):
    await page.goto(f"{base_url}/login?next=%2Forders")
    await page.get_by_role("link", name="Create an account").click()
    await page.wait_for_url(f"{base_url}/signup?next=%2Forders")


@pytest.mark.asyncio(loop_scope="session")
@pytest.mark.parametrize(
    "token,heading",
    [("good", "Email verified"), ("used", "We couldn't verify your email")],
)
async def test_verify_email_link(page, base_url, token, heading):
    await page.goto(f"{base_url}/verify-email?token={token}")
    await expect(page.get_by_role("heading", name=heading)).to_be_visible()


@pytest.mark.asyncio(loop_scope="session")
async def test_verify_device_link_signs_in(page, base_url):
    await page.goto(f"{base_url}/verify-device?token=good")
    await page.wait_for_url(f"{base_url}/")
    await expect(page.get_by_role("button", name="Account menu")).to_be_visible()
