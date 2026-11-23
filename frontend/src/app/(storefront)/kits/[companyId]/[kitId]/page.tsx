import type { Metadata } from "next";
import KitPage from "@views/KitPage";
import { kitKeys } from "../../../../../api/keys";
import { Hydrate } from "../../../../../api/server/hydrate";
import { getKit, loadPrimary } from "../../../../../api/server/storefront";
import { metaDescription } from "../../../../../lib/seo";

// Incremental static regeneration: rendered on first request, then cached for 60 s.
export const revalidate = 60;
export async function generateStaticParams() {
  return [];
}

export async function generateMetadata({
  params,
}: PageProps<"/kits/[companyId]/[kitId]">): Promise<Metadata> {
  const { companyId, kitId } = await params;
  const kit = await loadPrimary(() => getKit(companyId, kitId));
  const canonical = `/kits/${encodeURIComponent(companyId)}/${encodeURIComponent(kitId)}`;
  if (!kit) return { title: "Kit", alternates: { canonical } };
  const description = metaDescription(
    kit.description,
    `Build the ${kit.name} kit on ShopWave.`
  );
  return {
    title: kit.name,
    description,
    alternates: { canonical },
    openGraph: {
      type: "website",
      title: kit.name,
      description,
      url: canonical,
      images: kit.thumbnailUrl
        ? [{ url: kit.thumbnailUrl, alt: kit.name }]
        : undefined,
    },
  };
}

export default async function Page({
  params,
}: PageProps<"/kits/[companyId]/[kitId]">) {
  const { companyId, kitId } = await params;
  const kit = await loadPrimary(() => getKit(companyId, kitId));
  return (
    <Hydrate entries={[[kitKeys.detail(companyId, kitId), kit]]}>
      <KitPage />
    </Hydrate>
  );
}
