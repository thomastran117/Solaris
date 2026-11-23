import { describe, expect, it } from "vitest";
import { formatCalendarDate, formatCount, formatMoney } from "./format";

describe("formatMoney", () => {
  it("formats major units with the currency symbol", () => {
    expect(formatMoney(19.99, "USD")).toBe("$19.99");
    expect(formatMoney(1234.5, "usd")).toBe("$1,234.50");
    expect(formatMoney(0)).toBe("$0.00");
  });

  it("disambiguates other dollar currencies", () => {
    expect(formatMoney(5, "CAD")).toBe("CA$5.00");
  });

  it("falls back for codes Intl rejects", () => {
    expect(formatMoney(5, "NOT-A-CODE")).toBe("5.00 NOT-A-CODE");
  });
});

describe("existing formatters", () => {
  it("are locale- and zone-pinned", () => {
    expect(formatCount(12345)).toBe("12,345");
    expect(formatCalendarDate("2026-12-01")).toBe("Dec 1, 2026");
  });
});
