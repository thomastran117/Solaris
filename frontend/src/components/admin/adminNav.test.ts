import { describe, expect, it } from "vitest";
import { hasAdminAccess, visibleAdminNav, type AdminAccess } from "./adminNav";

function hrefs(access: AdminAccess): string[] {
  return visibleAdminNav(access).flatMap((g) => g.items.map((i) => i.href));
}

describe("visibleAdminNav", () => {
  it("shows only the overview to a user with no company or staff role", () => {
    expect(hrefs({ companyId: null, role: "USER", capabilities: [] })).toEqual([
      "/admin",
    ]);
  });

  it("gates company items on capabilities, mirroring the backend", () => {
    const visible = hrefs({
      companyId: "c1",
      role: "USER",
      capabilities: ["FULFILL_ORDERS"],
    });
    expect(visible).toContain("/admin/orders");
    expect(visible).toContain("/admin/drivers");
    // FULFILL_ORDERS staff can see products (as before), but not inventory.
    expect(visible).toContain("/admin/products");
    expect(visible).not.toContain("/admin/purchase-orders");
    expect(visible).not.toContain("/admin/team");
  });

  it("uses MANAGE_INVENTORY for purchase orders and transfers", () => {
    const productsOnly = hrefs({
      companyId: "c1",
      role: "USER",
      capabilities: ["MANAGE_PRODUCTS"],
    });
    expect(productsOnly).not.toContain("/admin/purchase-orders");

    const inventory = hrefs({
      companyId: "c1",
      role: "USER",
      capabilities: ["MANAGE_INVENTORY"],
    });
    expect(inventory).toEqual(
      expect.arrayContaining([
        "/admin/purchase-orders",
        "/admin/inventory-transfers",
        "/admin/bulk-import",
      ])
    );
  });

  it("ignores capabilities without a company", () => {
    expect(
      hrefs({ companyId: null, role: "USER", capabilities: ["MANAGE_COMPANY"] })
    ).toEqual(["/admin"]);
  });

  it("gates platform items on role", () => {
    const support = hrefs({
      companyId: null,
      role: "SUPPORT",
      capabilities: [],
    });
    expect(support).toContain("/admin/disputes");
    expect(support).not.toContain("/admin/feedback");

    const admin = hrefs({ companyId: null, role: "ADMIN", capabilities: [] });
    expect(admin).toEqual(
      expect.arrayContaining(["/admin/disputes", "/admin/feedback"])
    );
  });

  it("drops groups with no visible items", () => {
    const groups = visibleAdminNav({
      companyId: "c1",
      role: "USER",
      capabilities: ["MANAGE_QUOTES"],
    });
    expect(groups.map((g) => g.label)).toEqual([undefined, "Orders"]);
  });
});

describe("hasAdminAccess", () => {
  it("is true for company members and platform staff only", () => {
    expect(hasAdminAccess({ companyId: "c1", role: "USER" })).toBe(true);
    expect(hasAdminAccess({ companyId: null, role: "MODERATOR" })).toBe(true);
    expect(hasAdminAccess({ companyId: null, role: "USER" })).toBe(false);
    expect(hasAdminAccess({ companyId: null, role: null })).toBe(false);
  });
});
