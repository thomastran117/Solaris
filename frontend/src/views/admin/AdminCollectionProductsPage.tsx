"use client";

import { useMemo, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useSelector } from "react-redux";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  ChevronLeft,
  Plus,
  Trash2,
  RefreshCw,
  Pin,
  Zap,
  Search,
} from "lucide-react";
import { adminCollectionsApi } from "../../api/merchandising";
import { adminProductsApi } from "../../api/catalog";
import type { CollectionProductRow } from "../../types/collection";
import type { RootState } from "../../stores";
import { useAnims } from "../../hooks/useAnims";

const inputBase =
  "w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-ring focus:bg-muted transition-colors";

export default function AdminCollectionProductsPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const collectionId = id ?? null;
  const companyId = useSelector((s: RootState) => s.auth.companyId);
  const queryClient = useQueryClient();
  const { fadeInUp } = useAnims();

  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");

  const collectionKey = useMemo(
    () => ["admin-collections", "detail", { companyId, collectionId }],
    [companyId, collectionId]
  );

  const { data: collection } = useQuery({
    queryKey: collectionKey,
    queryFn: () =>
      adminCollectionsApi.get(companyId!, collectionId!).then((r) => r.data),
    enabled: !!companyId && collectionId !== null,
  });

  const { data, isLoading } = useQuery({
    queryKey: [
      "admin-collections",
      "products",
      { companyId, collectionId, page },
    ],
    queryFn: () =>
      adminCollectionsApi
        .listProducts(companyId!, collectionId!, page, 20)
        .then((r) => r.data),
    enabled: !!companyId && collectionId !== null,
  });

  // Product picker — only enabled for STATIC collections.
  const { data: productSearch } = useQuery({
    queryKey: ["admin-products", "search", { companyId, q: searchQuery }],
    queryFn: () =>
      adminProductsApi
        .list(companyId!, { q: searchQuery || undefined, size: 8 })
        .then((r) => r.data),
    enabled:
      !!companyId &&
      collection?.type === "STATIC" &&
      searchQuery.trim().length > 0,
  });

  function invalidate() {
    queryClient.invalidateQueries({
      queryKey: ["admin-collections", "products"],
    });
    queryClient.invalidateQueries({ queryKey: collectionKey });
  }

  const addMutation = useMutation({
    mutationFn: (productId: string) =>
      adminCollectionsApi.addProduct(companyId!, collectionId!, { productId }),
    onSuccess: () => {
      setSearchQuery("");
      invalidate();
    },
  });

  const removeMutation = useMutation({
    mutationFn: (productId: string) =>
      adminCollectionsApi.removeProduct(companyId!, collectionId!, productId),
    onSuccess: invalidate,
  });

  const updateMutation = useMutation({
    mutationFn: ({
      productId,
      boostWeight,
      pinnedRank,
    }: {
      productId: string;
      boostWeight: number | null;
      pinnedRank: number | null;
    }) =>
      adminCollectionsApi.updateProduct(companyId!, collectionId!, productId, {
        boostWeight,
        pinnedRank,
      }),
    onSuccess: invalidate,
  });

  const refreshMutation = useMutation({
    mutationFn: () => adminCollectionsApi.refresh(companyId!, collectionId!),
    onSuccess: invalidate,
  });

  if (!companyId) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center px-6">
        <p className="text-muted-foreground text-sm">
          Sign in with a vendor account to manage collections.
        </p>
      </div>
    );
  }

  const isDynamic = collection?.type === "DYNAMIC";

  return (
    <div>
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-10">
        <button
          type="button"
          onClick={() => router.push("/admin/collections")}
          className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors mb-6"
        >
          <ChevronLeft className="w-4 h-4" />
          Back to collections
        </button>

        <motion.header
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="flex items-end justify-between gap-4 mb-6"
        >
          <div>
            <p className="text-xs font-semibold text-link mb-2">
              {isDynamic ? "Dynamic collection" : "Static collection"} ·
              Products
            </p>
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              {collection?.name ?? "…"}
            </h1>
            {collection?.lastMaterialisedAt && (
              <p className="text-xs text-muted-foreground mt-1">
                Last refresh ·{" "}
                {new Date(collection.lastMaterialisedAt).toLocaleString()}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            {isDynamic && (
              <button
                type="button"
                onClick={() => refreshMutation.mutate()}
                disabled={refreshMutation.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-input text-sm font-semibold text-foreground hover:bg-muted transition-colors disabled:opacity-50"
              >
                <RefreshCw
                  className={`w-4 h-4 ${refreshMutation.isPending ? "animate-spin" : ""}`}
                />
                Refresh members
              </button>
            )}
            <button
              type="button"
              onClick={() =>
                router.push(`/admin/collections/${collectionId}/edit`)
              }
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full border border-input text-sm font-semibold text-foreground hover:bg-muted transition-colors"
            >
              Edit settings
            </button>
          </div>
        </motion.header>

        {/* Add product (static only) */}
        {!isDynamic && (
          <motion.div
            variants={fadeInUp}
            initial="hidden"
            animate="visible"
            className="rounded-xl border border-border bg-surface shadow-sm p-5 mb-6"
          >
            <p className="text-xs font-semibold text-link mb-3">
              Add a product
            </p>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className={`${inputBase} pl-9`}
                placeholder="Search products by name…"
              />
            </div>
            {productSearch && productSearch.items.length > 0 && (
              <div className="mt-3 rounded-xl border border-border bg-surface divide-y divide-border">
                {productSearch.items.map((p) => (
                  <div
                    key={p.id}
                    className="flex items-center justify-between gap-3 p-3"
                  >
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-foreground truncate">
                        {p.name}
                      </p>
                      <p className="text-xs text-muted-foreground truncate">
                        {p.sku ? `${p.sku} · ` : ""}
                        {p.currency} {p.price.toFixed(2)}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => addMutation.mutate(p.id)}
                      disabled={addMutation.isPending}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-primary hover:bg-primary-hover text-xs font-semibold text-primary-foreground transition-colors disabled:opacity-50"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Add
                    </button>
                  </div>
                ))}
              </div>
            )}
          </motion.div>
        )}

        {/* Member list */}
        <motion.div
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          className="rounded-xl border border-border bg-surface shadow-sm overflow-hidden"
        >
          {isLoading ? (
            <div className="p-6 space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div
                  key={i}
                  className="h-14 rounded-xl bg-surface animate-pulse"
                />
              ))}
            </div>
          ) : !data || data.items.length === 0 ? (
            <p className="p-10 text-center text-muted-foreground text-sm">
              {isDynamic
                ? "No products match these rules yet. Tweak the rule body or add tags to your products."
                : "No products in this collection yet. Search above to add some."}
            </p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs font-semibold text-muted-foreground bg-surface">
                <tr>
                  <th className="px-5 py-3">Product</th>
                  <th className="px-5 py-3">Source</th>
                  <th className="px-5 py-3">
                    <span className="inline-flex items-center gap-1.5">
                      <Zap className="w-3.5 h-3.5" />
                      Boost
                    </span>
                  </th>
                  <th className="px-5 py-3">
                    <span className="inline-flex items-center gap-1.5">
                      <Pin className="w-3.5 h-3.5" />
                      Pin order
                    </span>
                  </th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((row) => (
                  <CollectionProductRowView
                    key={row.id}
                    row={row}
                    onSave={(boostWeight, pinnedRank) =>
                      updateMutation.mutate({
                        productId: row.productId,
                        boostWeight,
                        pinnedRank,
                      })
                    }
                    onRemove={() => removeMutation.mutate(row.productId)}
                    saving={updateMutation.isPending}
                    removing={removeMutation.isPending}
                  />
                ))}
              </tbody>
            </table>
          )}
        </motion.div>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between mt-4 text-xs text-muted-foreground">
            <span>
              Page {data.page + 1} of {data.totalPages} · {data.totalElements}{" "}
              total
            </span>
            <div className="inline-flex gap-2">
              <button
                type="button"
                disabled={!data.hasPrevious}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="px-3 py-1.5 rounded-full border border-border hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                type="button"
                disabled={!data.hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1.5 rounded-full border border-border hover:bg-muted disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

interface RowProps {
  row: CollectionProductRow;
  onSave: (boostWeight: number | null, pinnedRank: number | null) => void;
  onRemove: () => void;
  saving: boolean;
  removing: boolean;
}

function CollectionProductRowView({
  row,
  onSave,
  onRemove,
  saving,
  removing,
}: RowProps) {
  const [boost, setBoost] = useState(
    row.boostWeight == null ? "" : String(row.boostWeight)
  );
  const [pinRank, setPinRank] = useState(
    row.pinnedRank == null ? "" : String(row.pinnedRank)
  );
  const dirty =
    (row.boostWeight == null ? "" : String(row.boostWeight)) !== boost ||
    (row.pinnedRank == null ? "" : String(row.pinnedRank)) !== pinRank;

  function handleSave() {
    const intOrNull = (s: string) => (s === "" ? null : Number(s));
    onSave(intOrNull(boost), intOrNull(pinRank));
  }

  return (
    <tr className="border-t border-border">
      <td className="px-5 py-3">
        <p className="font-medium text-foreground">{row.productName}</p>
        <p className="text-xs text-muted-foreground">
          {row.productSku ? `${row.productSku} · ` : ""}
          {row.productCurrency} {row.productPrice.toFixed(2)}
        </p>
      </td>
      <td className="px-5 py-3">
        <span
          className={[
            "inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border",
            row.source === "AUTO"
              ? "border-primary/40 bg-primary/10 text-link"
              : "border-border bg-muted text-muted-foreground",
          ].join(" ")}
        >
          {row.source === "AUTO" ? "Rule" : "Manual"}
        </span>
      </td>
      <td className="px-5 py-3">
        <input
          type="number"
          step="1"
          min="1"
          max="10"
          value={boost}
          onChange={(e) => setBoost(e.target.value)}
          className="w-20 rounded-lg border border-border bg-surface px-2 py-1 text-sm text-foreground"
          placeholder="—"
        />
      </td>
      <td className="px-5 py-3">
        <input
          type="number"
          step="1"
          min="0"
          value={pinRank}
          onChange={(e) => setPinRank(e.target.value)}
          className="w-20 rounded-lg border border-border bg-surface px-2 py-1 text-sm text-foreground"
          placeholder="—"
        />
      </td>
      <td className="px-5 py-3 text-right">
        <div className="inline-flex items-center gap-2">
          {dirty && (
            <button
              type="button"
              onClick={handleSave}
              disabled={saving}
              className="px-3 py-1.5 rounded-full bg-primary hover:bg-primary-hover text-xs font-semibold text-primary-foreground transition-colors disabled:opacity-50"
            >
              Save
            </button>
          )}
          <button
            type="button"
            onClick={onRemove}
            disabled={removing}
            aria-label="Remove from collection"
            className="p-2 rounded-lg text-muted-foreground hover:text-danger hover:bg-muted transition-colors disabled:opacity-40"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </td>
    </tr>
  );
}
