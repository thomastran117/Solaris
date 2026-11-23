"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { useSelector } from "react-redux";
import {
  ArrowRight,
  Gift,
  PackageSearch,
  Search,
  ShieldCheck,
  ShoppingBag,
  Star,
  Truck,
} from "lucide-react";
import { catalogApi } from "../api/catalog";
import { marketplaceCollectionsApi } from "../api/merchandising";
import { catalogKeys, homeKeys } from "../api/keys";
import CollectionCard from "../components/collection/CollectionCard";
import ProductCard from "../components/product/ProductCard";
import { Button } from "../components/ui/Button";
import { Container } from "../components/ui/Container";
import { EmptyState } from "../components/ui/EmptyState";
import { SectionHeader } from "../components/ui/SectionHeader";
import { Skeleton } from "../components/ui/Skeleton";
import { useAnims } from "../hooks/useAnims";
import {
  DEFAULT_CATALOG_PARAMS,
  catalogSearchRequest,
} from "../lib/catalogParams";
import { formatCount } from "../lib/format";
import { cn } from "../lib/cn";
import { selectActiveMarketplaceId } from "../stores/marketplaceSlice";

const POPULAR_COUNT = 8;
const CATEGORY_COUNT = 6;

/** What the platform actually provides; no invented numbers. */
const VALUE_PROPS = [
  {
    icon: ShieldCheck,
    title: "Secure checkout",
    description:
      "Payments are processed by Stripe; card details never touch our servers.",
  },
  {
    icon: Truck,
    title: "Live order tracking",
    description:
      "Follow every order from confirmation to delivery in real time.",
  },
  {
    icon: Star,
    title: "Rewards on every order",
    description:
      "Earn loyalty points with participating stores and redeem them at checkout.",
  },
  {
    icon: Gift,
    title: "Gift cards",
    description: "Send a balance anyone can spend across the marketplace.",
  },
] as const;

const browseHref = (params: Record<string, string>) =>
  `/browse?${new URLSearchParams(params).toString()}`;

export default function HomePage() {
  const router = useRouter();
  const marketplaceId = useSelector(selectActiveMarketplaceId);
  const { fadeInUp, stagger } = useAnims();
  const [query, setQuery] = useState("");

  const catalog = useQuery({
    queryKey: catalogKeys.browse(marketplaceId, DEFAULT_CATALOG_PARAMS),
    queryFn: () =>
      catalogApi
        .search(marketplaceId!, catalogSearchRequest(DEFAULT_CATALOG_PARAMS))
        .then((r) => r.data),
    enabled: !!marketplaceId,
  });

  const featured = useQuery({
    queryKey: homeKeys.featuredCollections(marketplaceId),
    queryFn: () =>
      marketplaceCollectionsApi
        .listFeatured(marketplaceId!)
        .then((r) => r.data),
    enabled: !!marketplaceId,
  });

  const products = catalog.data?.items.slice(0, POPULAR_COUNT) ?? [];
  const categories =
    catalog.data?.facets?.categories.slice(0, CATEGORY_COUNT) ?? [];
  const collections = featured.data ?? [];
  const heroImages = products
    .map((p) => p.thumbnailUrl ?? p.images?.[0]?.imageUrl)
    .filter((url): url is string => !!url)
    .slice(0, 4);

  function onSearch(e: FormEvent) {
    e.preventDefault();
    const q = query.trim();
    router.push(q ? browseHref({ q }) : "/browse");
  }

  const reveal = {
    variants: stagger,
    initial: "hidden",
    whileInView: "visible",
    viewport: { once: true, amount: 0.15 },
  } as const;

  return (
    <div>
      {/* Hero */}
      <section className="border-b bg-surface">
        <Container className="grid items-center gap-10 py-14 sm:py-20 lg:grid-cols-[1.1fr_1fr]">
          <motion.div {...reveal} className="max-w-xl">
            <motion.p
              variants={fadeInUp}
              className="text-sm font-medium text-link"
            >
              The ShopWave marketplace
            </motion.p>
            <motion.h1
              variants={fadeInUp}
              className="mt-3 text-4xl font-semibold tracking-tight text-foreground sm:text-5xl"
            >
              Shop independent stores in one place.
            </motion.h1>
            <motion.p
              variants={fadeInUp}
              className="mt-4 text-lg text-muted-foreground"
            >
              Browse products from merchants across the marketplace, save
              favourites to lists, and track every order to your door.
            </motion.p>

            <motion.form
              variants={fadeInUp}
              onSubmit={onSearch}
              role="search"
              className="mt-8 flex gap-2"
            >
              <div className="relative flex-1">
                <Search
                  aria-hidden
                  className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
                />
                <input
                  type="search"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="What are you looking for?"
                  aria-label="Search products"
                  className="h-11 w-full rounded-lg border border-input bg-background pl-9 pr-3 text-base text-foreground shadow-xs placeholder:text-muted-foreground focus-visible:border-ring focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/30 [&::-webkit-search-cancel-button]:hidden"
                />
              </div>
              <Button type="submit" size="lg">
                Search
              </Button>
            </motion.form>

            {categories.length > 0 && (
              <motion.div
                variants={fadeInUp}
                className="mt-5 flex flex-wrap items-center gap-2 text-sm"
              >
                <span className="text-muted-foreground">Popular:</span>
                {categories.slice(0, 4).map((c) => (
                  <Link
                    key={c.value}
                    href={browseHref({ category: c.value })}
                    className="rounded-full border bg-background px-3 py-1 text-foreground transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    {c.value}
                  </Link>
                ))}
              </motion.div>
            )}
          </motion.div>

          {heroImages.length >= 2 && (
            <motion.div
              {...reveal}
              aria-hidden
              className="hidden grid-cols-2 gap-4 lg:grid"
            >
              {heroImages.map((src, i) => (
                <motion.div
                  key={src}
                  variants={fadeInUp}
                  className={cn(
                    "overflow-hidden rounded-xl border bg-muted shadow-xs",
                    i % 2 === 1 && "translate-y-8"
                  )}
                >
                  <img
                    src={src}
                    alt=""
                    className="aspect-[4/5] size-full object-cover"
                  />
                </motion.div>
              ))}
            </motion.div>
          )}
        </Container>
      </section>

      {/* Shop by category */}
      {categories.length > 0 && (
        <Container className="py-12 sm:py-16">
          <SectionHeader
            title="Shop by category"
            action={
              <Button asChild variant="link">
                <Link href="/browse">
                  All products <ArrowRight aria-hidden />
                </Link>
              </Button>
            }
          />
          <motion.ul
            {...reveal}
            className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6"
          >
            {categories.map((c) => (
              <motion.li key={c.value} variants={fadeInUp}>
                <Link
                  href={browseHref({ category: c.value })}
                  className="flex h-full flex-col justify-between gap-6 rounded-xl border bg-surface p-4 shadow-xs transition-colors hover:bg-muted/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <span className="text-sm font-medium text-foreground">
                    {c.value}
                  </span>
                  <span className="text-xs text-muted-foreground tabular-nums">
                    {formatCount(c.count)}{" "}
                    {c.count === 1 ? "product" : "products"}
                  </span>
                </Link>
              </motion.li>
            ))}
          </motion.ul>
        </Container>
      )}

      {/* Featured collections */}
      {collections.length > 0 && (
        <Container className="py-12 sm:py-16">
          <SectionHeader
            kicker="Curated"
            title="Featured collections"
            subtitle="Hand-picked sets from stores across the marketplace."
          />
          <motion.div
            {...reveal}
            className="mt-6 grid grid-cols-2 gap-4 sm:gap-6 md:grid-cols-3 lg:grid-cols-4"
          >
            {collections.map((collection) => (
              <CollectionCard
                key={collection.id}
                collection={collection}
                variants={fadeInUp}
              />
            ))}
          </motion.div>
        </Container>
      )}

      {/* Popular picks */}
      <Container className="py-12 sm:py-16">
        <SectionHeader
          title="Popular picks"
          subtitle="A look at what's in the catalog right now."
          action={
            products.length > 0 ? (
              <Button asChild variant="outline">
                <Link href="/browse">
                  Browse all <ArrowRight aria-hidden />
                </Link>
              </Button>
            ) : undefined
          }
        />
        <div className="mt-6">
          {catalog.isPending && !!marketplaceId ? (
            <div
              className="grid grid-cols-2 gap-4 sm:gap-6 md:grid-cols-3 lg:grid-cols-4"
              aria-busy
            >
              {Array.from({ length: POPULAR_COUNT }, (_, i) => (
                <div key={i} className="space-y-3">
                  <Skeleton className="aspect-square w-full rounded-xl" />
                  <Skeleton className="h-4 w-3/4" />
                  <Skeleton className="h-4 w-1/3" />
                </div>
              ))}
            </div>
          ) : products.length > 0 ? (
            <motion.div
              {...reveal}
              className="grid grid-cols-2 gap-4 sm:gap-6 md:grid-cols-3 lg:grid-cols-4"
            >
              {products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  variants={fadeInUp}
                />
              ))}
            </motion.div>
          ) : (
            <EmptyState
              icon={PackageSearch}
              title={
                catalog.isError
                  ? "We couldn't load products"
                  : "No products yet"
              }
              description={
                catalog.isError
                  ? "Check your connection and try again."
                  : "Products will appear here once stores publish them."
              }
              action={
                catalog.isError ? (
                  <Button variant="outline" onClick={() => catalog.refetch()}>
                    Try again
                  </Button>
                ) : undefined
              }
            />
          )}
        </div>
      </Container>

      {/* Why ShopWave */}
      <section className="border-y bg-surface">
        <Container className="py-12 sm:py-16">
          <SectionHeader
            align="center"
            title="Built for how you shop"
            subtitle="The essentials, handled across every store on ShopWave."
          />
          <motion.ul
            {...reveal}
            className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4"
          >
            {VALUE_PROPS.map(({ icon: Icon, title, description }) => (
              <motion.li
                key={title}
                variants={fadeInUp}
                className="rounded-xl border bg-background p-5"
              >
                <span className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-link">
                  <Icon className="size-5" aria-hidden />
                </span>
                <h3 className="mt-4 text-base font-semibold text-foreground">
                  {title}
                </h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  {description}
                </p>
              </motion.li>
            ))}
          </motion.ul>
        </Container>
      </section>

      {/* Closing call to action */}
      <Container className="py-16 sm:py-20">
        <div className="flex flex-col items-start justify-between gap-6 rounded-xl border bg-surface p-8 shadow-xs sm:flex-row sm:items-center sm:p-10">
          <div className="max-w-xl">
            <h2 className="text-2xl font-semibold tracking-tight text-foreground">
              Find something you&apos;ll love
            </h2>
            <p className="mt-2 text-muted-foreground">
              Search the whole marketplace, or send a gift card and let them
              choose.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link href="/browse">
                <ShoppingBag aria-hidden />
                Start shopping
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link href="/gift-cards">
                <Gift aria-hidden />
                Gift cards
              </Link>
            </Button>
          </div>
        </div>
      </Container>
    </div>
  );
}
