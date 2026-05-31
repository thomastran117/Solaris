import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import TrackingPanel from "./TrackingPanel";

describe("TrackingPanel", () => {
  it("renders carrier name and tracking number", () => {
    render(<TrackingPanel trackingNumber="1Z999AA10123456784" carrier="UPS" />);
    expect(screen.getByText("1Z999AA10123456784")).toBeInTheDocument();
    // "UPS" appears in both the carrier label span and the link — use exact match on span
    expect(screen.getByText("UPS")).toBeInTheDocument();
  });

  it("renders correct deep-link href for UPS", () => {
    render(<TrackingPanel trackingNumber="1ZTRACK" carrier="UPS" />);
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", expect.stringContaining("ups.com"));
    expect(link).toHaveAttribute("href", expect.stringContaining("1ZTRACK"));
  });

  it("renders correct deep-link href for FedEx", () => {
    render(<TrackingPanel trackingNumber="FEDTRACK" carrier="FEDEX" />);
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", expect.stringContaining("fedex.com"));
    expect(link).toHaveAttribute("href", expect.stringContaining("FEDTRACK"));
  });

  it("renders correct deep-link href for USPS", () => {
    render(<TrackingPanel trackingNumber="9400USPS" carrier="USPS" />);
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", expect.stringContaining("usps.com"));
  });

  it("renders generic fallback for unknown carrier", () => {
    render(<TrackingPanel trackingNumber="GENERICTRACK" carrier="ROYAL_MAIL" />);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText(/No direct tracking link available/)).toBeInTheDocument();
  });

  it("renders fallback when carrier is null", () => {
    render(<TrackingPanel trackingNumber="NULLTRACK" carrier={null} />);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText(/No direct tracking link available/)).toBeInTheDocument();
  });

  it("tracking number has select-all class for copyability", () => {
    render(<TrackingPanel trackingNumber="SELECTME" carrier="UPS" />);
    expect(screen.getByText("SELECTME")).toHaveClass("select-all");
  });
});
