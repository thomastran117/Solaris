import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import OrderStatusTimeline from "./OrderStatusTimeline";

/** The list item for a step, which carries its state. */
const step = (label: string) =>
  screen.getByText(label, { exact: false }).closest("li");

describe("OrderStatusTimeline", () => {
  it("renders correct 5 steps for DELIVERY method", () => {
    render(
      <OrderStatusTimeline status="RESERVED" fulfillmentMethod="DELIVERY" />
    );
    const list = screen.getByRole("list", { name: "Order progress" });
    expect(list.querySelectorAll("li")).toHaveLength(5);
    for (const label of [
      "Reserved",
      "Paid",
      "Packed",
      "Shipped",
      "Delivered",
    ]) {
      expect(step(label)).toBeInTheDocument();
    }
  });

  it("renders Ready for Pickup step (not Shipped) for PICKUP method", () => {
    render(
      <OrderStatusTimeline status="RESERVED" fulfillmentMethod="PICKUP" />
    );
    expect(step("Ready for Pickup")).toBeInTheDocument();
    expect(step("Collected")).toBeInTheDocument();
    expect(
      screen.queryByText("Shipped", { exact: false })
    ).not.toBeInTheDocument();
  });

  it("marks steps before the current status as complete", () => {
    render(
      <OrderStatusTimeline status="PACKED" fulfillmentMethod="DELIVERY" />
    );
    // STATUS_ORDER[PACKED]=2 — Reserved (idx 0) and Paid (idx 1) are done
    expect(step("Reserved")).toHaveAttribute("data-state", "complete");
    expect(step("Paid")).toHaveAttribute("data-state", "complete");
    expect(step("Paid")).toHaveTextContent("(completed)");
  });

  it("marks the current step for assistive tech", () => {
    render(
      <OrderStatusTimeline status="PACKED" fulfillmentMethod="DELIVERY" />
    );
    expect(step("Packed")).toHaveAttribute("data-state", "current");
    expect(step("Packed")).toHaveAttribute("aria-current", "step");
    expect(
      screen
        .getAllByRole("listitem")
        .filter((li) => li.hasAttribute("aria-current"))
    ).toHaveLength(1);
  });

  it("does not show future steps as completed", () => {
    render(<OrderStatusTimeline status="PAID" fulfillmentMethod="DELIVERY" />);
    expect(step("Shipped")).toHaveAttribute("data-state", "upcoming");
    expect(step("Delivered")).toHaveAttribute("data-state", "upcoming");
  });

  it("shows PICKUP_READY step as current when hasPickupReady is true and status is PACKED", () => {
    render(
      <OrderStatusTimeline
        status="PACKED"
        fulfillmentMethod="PICKUP"
        hasPickupReady={true}
      />
    );
    // effectiveOrder becomes 3 — "Ready for Pickup" (idx 3) is current
    expect(step("Ready for Pickup")).toHaveAttribute("data-state", "current");
  });

  it("marks all steps complete when status is DELIVERED", () => {
    render(
      <OrderStatusTimeline status="DELIVERED" fulfillmentMethod="DELIVERY" />
    );
    expect(step("Delivered")).toHaveAttribute("data-state", "complete");
    expect(
      screen
        .queryAllByRole("listitem")
        .filter((li) => li.hasAttribute("aria-current"))
    ).toHaveLength(0);
  });
});
