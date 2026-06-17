import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProductComparisonTable } from "./ComparisonTable";
import type { ProductComparisonResponse } from "../../types/comparison";

function makeMatrix(): ProductComparisonResponse {
  return {
    products: [
      {
        productId: "p1",
        name: "Alpha",
        price: 10,
        currency: "USD",
        rating: 4.5,
        reviewCount: 12,
        stockStatus: "IN_STOCK",
        imageUrl: null,
      },
      {
        productId: "p2",
        name: "Beta",
        price: 20,
        currency: "USD",
        rating: null,
        reviewCount: 0,
        stockStatus: "OUT_OF_STOCK",
        imageUrl: null,
      },
    ],
    attributes: [
      {
        attributeName: "Material",
        valuesByProductId: { p1: "Steel", p2: null },
      },
    ],
  };
}

describe("ProductComparisonTable", () => {
  it("renders product columns and the attribute matrix", () => {
    render(<ProductComparisonTable matrix={makeMatrix()} onRemove={() => {}} />);

    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText("Beta")).toBeInTheDocument();
    expect(screen.getByText("Material")).toBeInTheDocument();
    expect(screen.getByText("Steel")).toBeInTheDocument();
  });

  it("renders an em dash for a product missing an attribute", () => {
    render(<ProductComparisonTable matrix={makeMatrix()} onRemove={() => {}} />);
    // p2 has no "Material" value -> placeholder.
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("marks the cheapest product with a Best badge", () => {
    render(<ProductComparisonTable matrix={makeMatrix()} onRemove={() => {}} />);
    // Cheapest (p1 @ 10) and best-rated (p1 @ 4.5) both flagged.
    expect(screen.getAllByText("Best").length).toBeGreaterThan(0);
  });

  it("shows the availability label per product", () => {
    render(<ProductComparisonTable matrix={makeMatrix()} onRemove={() => {}} />);
    expect(screen.getByText("In stock")).toBeInTheDocument();
    expect(screen.getByText("Out of stock")).toBeInTheDocument();
  });

  it("fires onRemove with the product id", async () => {
    const onRemove = vi.fn();
    render(<ProductComparisonTable matrix={makeMatrix()} onRemove={onRemove} />);
    screen.getByLabelText("Remove Alpha from comparison").click();
    expect(onRemove).toHaveBeenCalledWith("p1");
  });
});
