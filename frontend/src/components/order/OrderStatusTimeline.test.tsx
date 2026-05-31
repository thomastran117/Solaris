import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import OrderStatusTimeline from "./OrderStatusTimeline";

describe("OrderStatusTimeline", () => {
  it("renders correct 5 steps for DELIVERY method", () => {
    render(<OrderStatusTimeline status="RESERVED" fulfillmentMethod="DELIVERY" />);
    expect(screen.getByText("Reserved")).toBeInTheDocument();
    expect(screen.getByText("Paid")).toBeInTheDocument();
    expect(screen.getByText("Packed")).toBeInTheDocument();
    expect(screen.getByText("Shipped")).toBeInTheDocument();
    expect(screen.getByText("Delivered")).toBeInTheDocument();
  });

  it("renders Ready for Pickup step (not Shipped) for PICKUP method", () => {
    render(<OrderStatusTimeline status="RESERVED" fulfillmentMethod="PICKUP" />);
    expect(screen.getByText("Ready for Pickup")).toBeInTheDocument();
    expect(screen.getByText("Collected")).toBeInTheDocument();
    expect(screen.queryByText("Shipped")).not.toBeInTheDocument();
  });

  it("marks steps before current status as completed (green)", () => {
    render(<OrderStatusTimeline status="PACKED" fulfillmentMethod="DELIVERY" />);
    // STATUS_ORDER[PACKED]=2 — Reserved (idx 0) and Paid (idx 1) are done
    expect(screen.getByText("Reserved")).toHaveClass("text-green-400");
    expect(screen.getByText("Paid")).toHaveClass("text-green-400");
  });

  it("highlights the current active step in blue", () => {
    render(<OrderStatusTimeline status="PACKED" fulfillmentMethod="DELIVERY" />);
    // STATUS_ORDER[PACKED]=2 — Packed (idx 2) is active
    expect(screen.getByText("Packed")).toHaveClass("text-blue-300");
  });

  it("does not show future steps as completed", () => {
    render(<OrderStatusTimeline status="PAID" fulfillmentMethod="DELIVERY" />);
    expect(screen.getByText("Shipped")).toHaveClass("text-white/40");
    expect(screen.getByText("Delivered")).toHaveClass("text-white/40");
  });

  it("shows PICKUP_READY step as active when hasPickupReady is true and status is PACKED", () => {
    render(
      <OrderStatusTimeline status="PACKED" fulfillmentMethod="PICKUP" hasPickupReady={true} />
    );
    // effectiveOrder becomes 3 — "Ready for Pickup" (idx 3) should be active
    expect(screen.getByText("Ready for Pickup")).toHaveClass("text-blue-300");
  });

  it("marks all steps complete when status is DELIVERED", () => {
    render(<OrderStatusTimeline status="DELIVERED" fulfillmentMethod="DELIVERY" />);
    expect(screen.getByText("Delivered")).toHaveClass("text-green-400");
  });
});
