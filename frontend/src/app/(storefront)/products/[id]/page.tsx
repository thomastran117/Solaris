import type { Metadata } from "next";
import ProductPage from "@views/ProductPage";
import { productKeys } from "../../../../api/keys";
import { Hydrate } from "../../../../api/server/hydrate";
import {
  getCatalogProduct,
  loadPrimary,
} from "../../../../api/server/storefront";
import { getDefaultMarketplaceId } from "../../../../configuration/server";
import { jsonLd, metaDescription, productJsonLd } from "../../../../lib/seo";

// Incremental static regeneration: rendered on first request, then cached for 60 s.
export const revalidate = 60;
export async function generateStaticParams() {
  return [];
}

async function load(id: string) {
  const mp = getDefaultMarketplaceId();
  const product = mp
    ? await loadPrimary(() => getCatalogProduct(mp, id))
    : null;
  return { mp, product };
}

export async function generateMetadata({
  params,
}: PageProps<"/products/[id]">): Promise<Metadata> {
  const { id } = await params;
  const { product } = await load(id);
  const canonical = `/products/${encodeURIComponent(id)}`;
  if (!product) return { title: "Product", alternates: { canonical } };
  const image = product.thumbnailUrl ?? product.images?.[0]?.imageUrl;
  const description = metaDescription(
    product.description,
    `${product.name} on ShopWave.`
  );
  return {
    title: product.name,
    description,
    alternates: { canonical },
    openGraph: {
      type: "website",
      title: product.name,
      description,
      url: canonical,
      images: image ? [{ url: image, alt: product.name }] : undefined,
    },
  };
}

export default async function Page({ params }: PageProps<"/products/[id]">) {
  const { id } = await params;
  const { mp, product } = await load(id);
  return (
    <Hydrate entries={[[productKeys.detail(mp, id), product]]}>
      {product ? (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: jsonLd(
              productJsonLd(product, `/products/${encodeURIComponent(id)}`)
            ),
          }}
        />
      ) : null}
      <ProductPage />
    </Hydrate>
  );
}
