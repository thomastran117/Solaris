import "server-only";
import { cache } from "react";
import { notFound } from "next/navigation";
import type { CatalogProduct } from "../catalog";
import type { PagedAnnouncements } from "../follow";
import type { CollectionProductsPage } from "../merchandising";
import { COLLECTION_MEMBERS_PAGE_SIZE } from "../keys";
import {
  catalogSearchRequest,
  type CatalogUrlParams,
} from "../../lib/catalogParams";
import type { Collection } from "../../types/collection";
import type { PublicCompanyResponse } from "../../types/company";
import type { ProductKit } from "../../types/kit";
import type { PublicSavedList } from "../../types/savedList";
import type { CatalogSearchResponse } from "../../types/search";
import { ApiHttpError, serverGet } from "./http";

/**
 * Public storefront reads for server-rendered pages. Each getter mirrors the request
 * the corresponding client view makes (same path, page and size), so the payload it
 * seeds into TanStack Query is exactly what the view would have fetched itself.
 *
 * `cache()` deduplicates calls within one render, so `generateMetadata` and the page
 * share a single backend request.
 */

const seg = encodeURIComponent;

export const getFeaturedCollections = cache((mp: string) =>
  serverGet<Collection[]>(`/marketplaces/${seg(mp)}/collections/featured`)
);

export const searchMarketplaceCatalog = (mp: string, p: CatalogUrlParams) =>
  serverGet<CatalogSearchResponse>(
    `/marketplaces/${seg(mp)}/catalog/products`,
    {
      searchParams: { ...catalogSearchRequest(p) },
    }
  );

export const getCatalogProduct = cache((mp: string, productId: string) =>
  serverGet<CatalogProduct>(
    `/marketplaces/${seg(mp)}/catalog/products/${seg(productId)}`
  )
);

export const getPublicCompany = cache((companyId: string) =>
  serverGet<PublicCompanyResponse>(`/companies/${seg(companyId)}/public`)
);

export const searchCompanyCatalog = (companyId: string, p: CatalogUrlParams) =>
  serverGet<CatalogSearchResponse>(
    `/companies/${seg(companyId)}/catalog/search`,
    {
      searchParams: { ...catalogSearchRequest(p) },
    }
  );

export const getPublishedAnnouncements = (
  companyId: string,
  page = 0,
  size = 20
) =>
  serverGet<PagedAnnouncements>(`/companies/${seg(companyId)}/announcements`, {
    searchParams: { page, size },
  });

export const getCollectionBySlug = cache((mp: string, slug: string) =>
  serverGet<Collection>(`/marketplaces/${seg(mp)}/collections/${seg(slug)}`)
);

export const getCollectionMembers = (mp: string, slug: string) =>
  serverGet<CollectionProductsPage>(
    `/marketplaces/${seg(mp)}/collections/${seg(slug)}/products`,
    { searchParams: { page: 0, size: COLLECTION_MEMBERS_PAGE_SIZE } }
  );

export const getKit = cache((companyId: string, kitId: string) =>
  serverGet<ProductKit>(`/companies/${seg(companyId)}/kits/${seg(kitId)}`)
);

export const getPublicSavedList = cache((slug: string) =>
  serverGet<PublicSavedList>(`/saved-lists/public/${seg(slug)}`)
);

/** True when the backend says the entity does not exist (or the ID is malformed). */
export function isMissing(err: unknown): boolean {
  return (
    err instanceof ApiHttpError && (err.status === 404 || err.status === 400)
  );
}

/**
 * Loads the entity a page is about. A missing entity renders the not-found page with
 * HTTP 404. Any other failure (backend down, 5xx, timeout) resolves to `null`, so the
 * page still renders and the client view retries in the browser.
 */
export async function loadPrimary<T>(
  load: () => Promise<T>
): Promise<T | null> {
  try {
    return await load();
  } catch (err) {
    if (isMissing(err)) notFound();
    console.error(
      "[ssr] primary storefront fetch failed; rendering without prefetch",
      err
    );
    return null;
  }
}

/** Loads supplementary data; any failure just skips the prefetch. */
export async function loadOptional<T>(
  load: () => Promise<T>
): Promise<T | null> {
  try {
    return await load();
  } catch (err) {
    if (!isMissing(err)) {
      console.error(
        "[ssr] optional storefront fetch failed; skipping prefetch",
        err
      );
    }
    return null;
  }
}
