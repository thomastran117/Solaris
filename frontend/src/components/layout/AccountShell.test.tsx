import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { setMockUrl } from "../../test/nextNavigation";
import { renderWithProviders, signedIn } from "../../test/renderWithProviders";
import { AccountShell } from "./AccountShell";

describe("AccountShell", () => {
  it("lists the account destinations in both navs and marks the current page", () => {
    setMockUrl("/orders/42");
    renderWithProviders(<AccountShell>Order detail</AccountShell>, {
      auth: signedIn(),
    });

    const navs = screen.getAllByRole("navigation", { name: "Account" });
    // Desktop sidebar and mobile tab strip share the same links.
    expect(navs).toHaveLength(2);
    for (const nav of navs) {
      const orders = within(nav).getByRole("link", { name: "Orders" });
      expect(orders).toHaveAttribute("aria-current", "page");
      expect(
        within(nav).getByRole("link", { name: "Saved lists" })
      ).not.toHaveAttribute("aria-current");
      // Gift cards belong to the storefront, not the account area.
      expect(
        within(nav).queryByRole("link", { name: "Gift cards" })
      ).not.toBeInTheDocument();
    }
    expect(screen.getByText("sam@example.test")).toBeInTheDocument();
    expect(screen.getByText("Order detail")).toBeInTheDocument();
  });
});
