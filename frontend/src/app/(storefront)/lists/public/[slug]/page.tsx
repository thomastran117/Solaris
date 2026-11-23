import type { Metadata } from "next";
import PublicSavedListPage from "@views/PublicSavedListPage";
import { savedListKeys } from "../../../../../api/keys";
import { Hydrate } from "../../../../../api/server/hydrate";
import {
  getPublicSavedList,
  loadPrimary,
} from "../../../../../api/server/storefront";
import { metaDescription } from "../../../../../lib/seo";

// Incremental static regeneration: rendered on first request, then cached for 60 s.
export const revalidate = 60;
export async function generateStaticParams() {
  return [];
}

export async function generateMetadata({
  params,
}: PageProps<"/lists/public/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const list = await loadPrimary(() => getPublicSavedList(slug));
  return {
    title: list ? list.name : "Saved list",
    description: list
      ? metaDescription(list.description, `A list shared on ShopWave.`)
      : undefined,
    // Shared lists are user-generated: viewable by link, kept out of search indexes.
    robots: { index: false, follow: true },
  };
}

export default async function Page({
  params,
}: PageProps<"/lists/public/[slug]">) {
  const { slug } = await params;
  const list = await loadPrimary(() => getPublicSavedList(slug));
  return (
    <Hydrate entries={[[savedListKeys.public(slug), list]]}>
      <PublicSavedListPage />
    </Hydrate>
  );
}
