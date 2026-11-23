/**
 * TanStack Query keys for data that public pages prefetch on the server.
 *
 * Server pages seed these keys through `HydrationBoundary`, and the client views
 * query the same keys, so the shapes must stay identical on both sides. Mutations
 * elsewhere invalidate by prefix (for example `["savedLists", "list"]`), so keep the
 * leading segments stable when adding keys.
 */
import { catalogKeyParts, type CatalogUrlParams } from "../lib/catalogParams";
import type { SavedListType } from "../types/savedList";

export const homeKeys = {
  featuredCollections: (mp: string | null) =>
    ["home", "featured-collections", mp] as const,
};

export const catalogKeys = {
  browse: (mp: string | null, p: CatalogUrlParams) =>
    ["catalog", "browse", mp, ...catalogKeyParts(p)] as const,
};

export const productKeys = {
  detail: (mp: string | null, productId: string | undefined) =>
    ["product", mp, productId] as const,
};

export const companyKeys = {
  public: (companyId: string | null) =>
    ["company", "public", companyId] as const,
  catalog: (companyId: string | null, p: CatalogUrlParams) =>
    ["company", "catalog", companyId, ...catalogKeyParts(p)] as const,
  announcements: (companyId: string | null, page: number) =>
    ["announcements", companyId, page] as const,
};

export const collectionKeys = {
  detail: (mp: string | null, slug: string | undefined) =>
    ["collection", "detail", mp, slug] as const,
  members: (mp: string | null, slug: string | undefined) =>
    ["collection", "members", mp, slug] as const,
  membersCatalog: (
    mp: string | null,
    slug: string | undefined,
    productIds: string[]
  ) => ["collection", "members-catalog", mp, slug, productIds] as const,
};

export const kitKeys = {
  detail: (companyId: string | undefined, kitId: string | undefined) =>
    ["kit", companyId, kitId] as const,
};

export const savedListKeys = {
  list: (type?: SavedListType) =>
    ["savedLists", "list", { type: type ?? null }] as const,
  detail: (id: string) => ["savedLists", "detail", id] as const,
  public: (slug: string) => ["savedLists", "public", slug] as const,
};

/** Size of the collection member page that both the server and the view request. */
export const COLLECTION_MEMBERS_PAGE_SIZE = 50;
