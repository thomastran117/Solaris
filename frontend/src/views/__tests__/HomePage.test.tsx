import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { makeStore } from "../../stores";
import { marketplaceStateWithDefault } from "../../stores/marketplaceSlice";
import { mockRouter } from "../../test/nextNavigation";
import { renderWithProviders } from "../../test/renderWithProviders";

const { search, listFeatured } = vi.hoisted(() => ({
  search: vi.fn(),
  listFeatured: vi.fn(),
}));
vi.mock("../../api/catalog", () => ({ catalogApi: { search } }));
vi.mock("../../api/merchandising", () => ({
  marketplaceCollectionsApi: { listFeatured },
}));

const HomePage = (await import("../HomePage")).default;

const product = (id: string, name: string) => ({
  id,
  name,
  price: 49.5,
  currency: "USD",
  category: "Audio",
  thumbnailUrl: null,
  images: [],
});

function renderHome() {
  const base = makeStore().getState();
  const store = makeStore({
    auth: { ...base.auth, sessionRestored: true },
    marketplace: marketplaceStateWithDefault("mp-1"),
  });
  return renderWithProviders(<HomePage />, { store });
}

describe("HomePage", () => {
  beforeEach(() => {
    search.mockReset();
    listFeatured.mockReset().mockResolvedValue({ data: [] });
  });

  it("shows live products, categories and featured collections", async () => {
    search.mockResolvedValue({
      data: {
        items: [product("p1", "Aurora Headphones"), product("p2", "Desk Lamp")],
        facets: {
          categories: [{ value: "Audio", count: 12 }],
          brands: [],
          priceRanges: [],
        },
      },
    });
    listFeatured.mockResolvedValue({
      data: [
        {
          id: "c1",
          slug: "winter-sound",
          name: "Winter Sound Picks",
          productCount: 3,
          type: "MANUAL",
        },
      ],
    });
    renderHome();

    expect(await screen.findByText("Aurora Headphones")).toBeInTheDocument();
    expect(screen.getAllByText("$49.50")).toHaveLength(2);
    expect(screen.getByText("Winter Sound Picks")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Audio\s*12 products/ })
    ).toHaveAttribute("href", "/browse?category=Audio");
    // Popular picks reuse the default browse request.
    expect(search).toHaveBeenCalledWith(
      "mp-1",
      expect.objectContaining({ page: 0 })
    );
  });

  it("shows an honest empty state when the catalog is empty", async () => {
    search.mockResolvedValue({
      data: {
        items: [],
        facets: { categories: [], brands: [], priceRanges: [] },
      },
    });
    renderHome();
    expect(await screen.findByText("No products yet")).toBeInTheDocument();
    expect(screen.queryByText("Featured collections")).not.toBeInTheDocument();
  });

  it("searches the marketplace from the hero", async () => {
    search.mockResolvedValue({
      data: {
        items: [],
        facets: { categories: [], brands: [], priceRanges: [] },
      },
    });
    const user = userEvent.setup();
    renderHome();
    const hero = screen.getAllByRole("searchbox", {
      name: "Search products",
    })[0];
    await user.type(hero, "headphones");
    await user.click(screen.getByRole("button", { name: "Search" }));
    expect(mockRouter.push).toHaveBeenCalledWith("/browse?q=headphones");
  });
});
