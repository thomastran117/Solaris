"use client";

import { useSelector } from "react-redux";
import Link from "next/link";
import { motion } from "framer-motion";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { TrendingDown, X } from "lucide-react";
import { SectionHeader } from "../components/ui/SectionHeader";
import { priceWatchersApi } from "../api/priceWatchers";
import type { PriceWatcher } from "../types/priceWatchers";
import type { RootState } from "../stores";
import { useAnims } from "../hooks/useAnims";

function formatCents(cents: number): string {
  return `$${(cents / 100).toFixed(2)}`;
}

function WatchCard({
  watch,
  onUnwatch,
}: {
  watch: PriceWatcher;
  onUnwatch: () => void;
}) {
  return (
    <div className="rounded-xl border border-border bg-surface p-5 flex items-center justify-between gap-4">
      <div className="flex items-center gap-3 min-w-0">
        <div className="bg-primary/10 border border-border rounded-xl p-3 shrink-0">
          <TrendingDown className="h-5 w-5 text-link" />
        </div>
        <div className="min-w-0">
          <Link
            href={`/products/${watch.productId}`}
            className="text-sm font-semibold text-foreground hover:text-link transition-colors truncate block"
          >
            {watch.productName}
          </Link>
          <p className="text-xs text-muted-foreground mt-0.5">
            Alert if drops below{" "}
            <span className="text-foreground font-medium">
              {formatCents(watch.watchPriceCents)}
            </span>
          </p>
        </div>
      </div>
      <button
        onClick={onUnwatch}
        aria-label={`Stop watching ${watch.productName}`}
        className="p-2 rounded-xl border border-border text-muted-foreground hover:text-danger hover:border-danger/40 hover:bg-danger-subtle transition-all shrink-0"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}

export default function PriceWatchesPage() {
  const { fadeInUp, stagger } = useAnims();
  const queryClient = useQueryClient();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);

  const { data, isLoading } = useQuery({
    queryKey: ["price-watches"],
    queryFn: () => priceWatchersApi.list().then((r) => r.data),
    enabled: !!accessToken,
  });

  const unwatchMutation = useMutation({
    mutationFn: (productId: string) => priceWatchersApi.unwatch(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["price-watches"] });
    },
  });

  const watches = data?.content ?? [];

  return (
    <div>
      <div className="relative z-10 pt-24 pb-32">
        <div className="max-w-3xl">
          <motion.div
            variants={fadeInUp}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, amount: 0.25 }}
          >
            <SectionHeader
              as="h1"
              kicker="My Alerts"
              title="Price Drop Watches"
              subtitle="We'll email you the moment a watched product's price drops."
              align="left"
            />
          </motion.div>

          <div className="mt-10">
            {!accessToken ? (
              <div className="rounded-xl border border-border bg-surface p-8 text-center">
                <TrendingDown className="w-10 h-10 text-link mx-auto mb-3" />
                <p className="text-muted-foreground text-sm">
                  <Link
                    href="/login"
                    className="text-link hover:text-link underline underline-offset-2"
                  >
                    Sign in
                  </Link>{" "}
                  to view your price watches.
                </p>
              </div>
            ) : isLoading ? (
              <div className="flex justify-center py-16">
                <div className="w-8 h-8 rounded-full border-2 border-primary/40 border-t-primary animate-spin" />
              </div>
            ) : watches.length === 0 ? (
              <div className="rounded-xl border border-border bg-surface p-10 text-center">
                <TrendingDown className="w-10 h-10 text-link mx-auto mb-4" />
                <p className="text-muted-foreground text-sm">
                  No price watches yet. Visit a product page and click{" "}
                  <span className="text-foreground font-medium">
                    Watch for price drops
                  </span>{" "}
                  to get started.
                </p>
              </div>
            ) : (
              <motion.div
                variants={stagger}
                initial="hidden"
                whileInView="visible"
                viewport={{ once: true, amount: 0.15 }}
                className="flex flex-col gap-3"
              >
                {watches.map((watch) => (
                  <motion.div key={watch.id} variants={fadeInUp}>
                    <WatchCard
                      watch={watch}
                      onUnwatch={() => unwatchMutation.mutate(watch.productId)}
                    />
                  </motion.div>
                ))}
              </motion.div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
