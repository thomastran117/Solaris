import { describe, expect, it } from "vitest";
import {
  catalogKeys,
  collectionKeys,
  companyKeys,
  homeKeys,
  kitKeys,
  productKeys,
  savedListKeys,
} from "./keys";
import { parseCatalogUrlParams } from "../lib/catalogParams";

/**
 * These shapes are a contract between server prefetch and client queries, and with
 * existing prefix invalidations. A change here must be deliberate.
 */
describe("query keys", () => {
  const p = parseCatalogUrlParams(
    new URLSearchParams("q=lamp&category=Home&page=1")
  );

  it("keeps the established key shapes", () => {
    expect(homeKeys.featuredCollections("mp")).toEqual([
      "home",
      "featured-collections",
      "mp",
    ]);
    expect(catalogKeys.browse("mp", p)).toEqual([
      "catalog",
      "browse",
      "mp",
      "lamp",
      "Home",
      null,
      null,
      null,
      "relevance",
      "desc",
      1,
    ]);
    expect(productKeys.detail("mp", "p1")).toEqual(["product", "mp", "p1"]);
    expect(companyKeys.public("c1")).toEqual(["company", "public", "c1"]);
    expect(companyKeys.catalog("c1", p)).toEqual([
      "company",
      "catalog",
      "c1",
      "lamp",
      "Home",
      null,
      null,
      null,
      "relevance",
      "desc",
      1,
    ]);
    expect(companyKeys.announcements("c1", 0)).toEqual([
      "announcements",
      "c1",
      0,
    ]);
    expect(collectionKeys.detail("mp", "s")).toEqual([
      "collection",
      "detail",
      "mp",
      "s",
    ]);
    expect(collectionKeys.members("mp", "s")).toEqual([
      "collection",
      "members",
      "mp",
      "s",
    ]);
    expect(collectionKeys.membersCatalog("mp", "s", ["a", "b"])).toEqual([
      "collection",
      "members-catalog",
      "mp",
      "s",
      ["a", "b"],
    ]);
    expect(kitKeys.detail("c1", "k1")).toEqual(["kit", "c1", "k1"]);
    expect(savedListKeys.public("slug")).toEqual([
      "savedLists",
      "public",
      "slug",
    ]);
    expect(savedListKeys.list()).toEqual([
      "savedLists",
      "list",
      { type: null },
    ]);
  });

  it("produces the same browse key from browser and server parameter sources", () => {
    const server = parseCatalogUrlParams({
      q: "lamp",
      category: "Home",
      page: "1",
    });
    expect(catalogKeys.browse("mp", server)).toEqual(
      catalogKeys.browse("mp", p)
    );
  });
});
