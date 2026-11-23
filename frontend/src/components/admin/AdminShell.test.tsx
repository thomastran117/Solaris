import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { setMockUrl } from "../../test/nextNavigation";
import { renderWithProviders, signedIn } from "../../test/renderWithProviders";

const { describeMyAccess } = vi.hoisted(() => ({ describeMyAccess: vi.fn() }));
vi.mock("../../api/team", () => ({ teamApi: { describeMyAccess } }));
vi.mock("../../api", () => ({ default: { post: vi.fn(), get: vi.fn() } }));

const { AdminShell } = await import("./AdminShell");

function sidebar() {
  // The desktop sidebar and the (closed) mobile sheet share the nav; the
  // sheet isn't rendered until opened, so there is exactly one here.
  return screen.getByRole("navigation", { name: "Seller dashboard" });
}

describe("AdminShell", () => {
  beforeEach(() => {
    describeMyAccess.mockReset();
  });

  it("lists only the areas the member's capabilities allow", async () => {
    describeMyAccess.mockResolvedValue({
      data: { capabilities: ["FULFILL_ORDERS", "MANAGE_COMPANY"] },
    });
    setMockUrl("/admin/orders");
    renderWithProviders(<AdminShell>content</AdminShell>, {
      auth: signedIn({ companyId: "c1" }),
    });

    const orders = await within(sidebar()).findByRole("link", {
      name: "Orders",
    });
    expect(orders).toHaveAttribute("aria-current", "page");
    expect(
      within(sidebar()).getByRole("link", { name: "Team" })
    ).toBeInTheDocument();
    expect(
      within(sidebar()).queryByRole("link", { name: "Purchase orders" })
    ).not.toBeInTheDocument();
    expect(describeMyAccess).toHaveBeenCalledWith("c1");
  });

  it("shows platform tools to staff without a company", () => {
    renderWithProviders(<AdminShell>content</AdminShell>, {
      auth: signedIn({ role: "SUPPORT" }),
    });
    expect(
      within(sidebar()).getByRole("link", { name: "Chargebacks" })
    ).toHaveAttribute("href", "/admin/disputes");
    expect(describeMyAccess).not.toHaveBeenCalled();
  });

  it("renders its children in the main landmark", () => {
    renderWithProviders(<AdminShell>Page body</AdminShell>, {
      auth: signedIn({ role: "ADMIN" }),
    });
    expect(screen.getByRole("main")).toHaveTextContent("Page body");
    expect(screen.getByRole("link", { name: /Back to store/ })).toHaveAttribute(
      "href",
      "/"
    );
  });
});
