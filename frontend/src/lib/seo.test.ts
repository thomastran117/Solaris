import { describe, expect, it } from "vitest";
import {
  catalogCanonical,
  isCatalogIndexable,
  jsonLd,
  metaDescription,
} from "./seo";
import { parseCatalogUrlParams } from "./catalogParams";

describe("seo helpers", () => {
  it("escapes < in JSON-LD so a value cannot close the script tag", () => {
    const out = jsonLd({
      description: "evil </script><script>alert(1)</script>",
    });
    expect(out).not.toContain("</script>");
    expect(JSON.parse(out).description).toBe(
      "evil </script><script>alert(1)</script>"
    );
  });

  it("normalises and truncates descriptions on a word boundary", () => {
    expect(metaDescription("  a\n\n b  ", "fallback")).toBe("a b");
    expect(metaDescription(null, "fallback")).toBe("fallback");
    const long = metaDescription("word ".repeat(100), "f", 40);
    expect(long.length).toBeLessThanOrEqual(40);
    expect(long.endsWith("…")).toBe(true);
    expect(long).not.toMatch(/wor…$/);
  });

  it("builds catalog canonicals from category, brand and page only", () => {
    const p = parseCatalogUrlParams(
      new URLSearchParams(
        "q=x&category=Audio&brand=Acme&sort=price&direction=asc&page=2&minPrice=5"
      )
    );
    expect(catalogCanonical("/browse", p)).toBe(
      "/browse?category=Audio&brand=Acme&page=2"
    );
    expect(
      catalogCanonical("/browse", parseCatalogUrlParams(new URLSearchParams()))
    ).toBe("/browse");
  });

  it("keeps free-text and price-filtered listings out of the index", () => {
    expect(
      isCatalogIndexable(
        parseCatalogUrlParams(new URLSearchParams("category=Audio"))
      )
    ).toBe(true);
    expect(
      isCatalogIndexable(parseCatalogUrlParams(new URLSearchParams("q=lamp")))
    ).toBe(false);
    expect(
      isCatalogIndexable(
        parseCatalogUrlParams(new URLSearchParams("maxPrice=9"))
      )
    ).toBe(false);
  });
});
