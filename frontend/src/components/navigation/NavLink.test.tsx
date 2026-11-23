import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import NavLink, { isNavLinkActive } from "./NavLink";
import { setMockUrl } from "../../test/nextNavigation";

describe("isNavLinkActive", () => {
  it.each([
    ["/", "/", false, true],
    ["/browse", "/", false, false],
    ["/admin/products", "/admin/products", false, true],
    ["/admin/products/123", "/admin/products", false, true],
    ["/admin/products/123", "/admin/products", true, false],
    ["/admin/productsx", "/admin/products", false, false],
    ["/lists", "/lists?tab=mine", false, true],
  ])("pathname %s vs to %s (end=%s) -> %s", (pathname, to, end, expected) => {
    expect(isNavLinkActive(pathname, to, end)).toBe(expected);
  });
});

describe("NavLink", () => {
  it("applies the active class callback and aria-current on the matching route", () => {
    setMockUrl("/orders/42");
    render(
      <>
        <NavLink
          to="/orders"
          className={({ isActive }) => (isActive ? "on" : "off")}
        >
          Orders
        </NavLink>
        <NavLink
          to="/browse"
          className={({ isActive }) => (isActive ? "on" : "off")}
        >
          Browse
        </NavLink>
      </>
    );
    const orders = screen.getByRole("link", { name: "Orders" });
    expect(orders).toHaveClass("on");
    expect(orders).toHaveAttribute("aria-current", "page");
    expect(orders).toHaveAttribute("href", "/orders");
    const browse = screen.getByRole("link", { name: "Browse" });
    expect(browse).toHaveClass("off");
    expect(browse).not.toHaveAttribute("aria-current");
  });
});
