import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { mockRouter } from "../../test/nextNavigation";
import { renderWithProviders, signedIn } from "../../test/renderWithProviders";

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("../../api", () => ({ default: { post, get: vi.fn() } }));

const { SiteHeader } = await import("./SiteHeader");

describe("SiteHeader", () => {
  beforeEach(() => {
    post.mockReset().mockResolvedValue({ data: {} });
  });

  it("offers sign in to visitors", () => {
    renderWithProviders(<SiteHeader />);
    expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute(
      "href",
      "/login"
    );
    expect(
      screen.queryByRole("button", { name: "Account menu" })
    ).not.toBeInTheDocument();
  });

  it("links the primary destinations", () => {
    renderWithProviders(<SiteHeader />);
    const nav = screen.getAllByRole("navigation", { name: "Primary" })[0];
    expect(within(nav).getByRole("link", { name: "Browse" })).toHaveAttribute(
      "href",
      "/browse"
    );
  });

  it("searches from the header", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SiteHeader />);
    const [box] = screen.getAllByRole("searchbox", { name: "Search products" });
    await user.type(box, "desk lamp{Enter}");
    expect(mockRouter.push).toHaveBeenCalledWith("/browse?q=desk%20lamp");
  });

  it("shows account links but no seller link for a shopper", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SiteHeader />, { auth: signedIn() });
    await user.click(screen.getByRole("button", { name: "Account menu" }));
    const menu = await screen.findByRole("menu");
    expect(within(menu).getByText("sam@example.test")).toBeInTheDocument();
    expect(
      within(menu).getByRole("menuitem", { name: "Orders" })
    ).toHaveAttribute("href", "/orders");
    expect(
      within(menu).queryByRole("menuitem", { name: "Seller dashboard" })
    ).not.toBeInTheDocument();
  });

  it("shows the seller dashboard to company members", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SiteHeader />, {
      auth: signedIn({ companyId: "c1", tier: "PREMIUM" }),
    });
    await user.click(screen.getByRole("button", { name: "Account menu" }));
    const menu = await screen.findByRole("menu");
    expect(within(menu).getByText("Premium")).toBeInTheDocument();
    expect(
      within(menu).getByRole("menuitem", { name: "Seller dashboard" })
    ).toHaveAttribute("href", "/admin");
  });

  it("signs out: revokes, clears credentials and goes to login", async () => {
    const user = userEvent.setup();
    const { store } = renderWithProviders(<SiteHeader />, { auth: signedIn() });
    await user.click(screen.getByRole("button", { name: "Account menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "Sign out" }));
    expect(post).toHaveBeenCalledWith("/auth/logout");
    expect(store.getState().auth.accessToken).toBeNull();
    expect(mockRouter.push).toHaveBeenCalledWith("/login");
  });

  it("still signs out locally when the revoke call fails", async () => {
    post.mockRejectedValueOnce(new Error("expired"));
    const user = userEvent.setup();
    const { store } = renderWithProviders(<SiteHeader />, { auth: signedIn() });
    await user.click(screen.getByRole("button", { name: "Account menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "Sign out" }));
    expect(store.getState().auth.accessToken).toBeNull();
  });

  it("opens the mobile menu with account destinations", async () => {
    const user = userEvent.setup();
    renderWithProviders(<SiteHeader />, { auth: signedIn() });
    await user.click(screen.getByRole("button", { name: "Open menu" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByRole("link", { name: "Saved lists" })
    ).toHaveAttribute("href", "/lists");
    expect(
      within(dialog).getByRole("button", { name: "Sign out" })
    ).toBeInTheDocument();
  });
});
