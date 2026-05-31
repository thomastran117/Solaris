import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, useReducedMotion, type Variants } from "framer-motion";
import { Megaphone, Building2, Bell, BellOff, UserMinus, Rss } from "lucide-react";
import { followApi } from "../api/follow";
import type { AnnouncementType } from "../types/company";
import NavyGridGlowBackground from "../components/layout/NavyGridGlowBackground";
import SectionTitle from "../components/section/SectionTitle";
import SectionGlow from "../components/section/SectionGlow";

const useAnims = () => {
  const reduced = useReducedMotion();
  const fadeInUp: Variants = reduced
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.2 } } }
    : { hidden: { opacity: 0, y: 14 }, visible: { opacity: 1, y: 0, transition: { duration: 0.4 } } };
  const stagger: Variants = {
    hidden: {},
    visible: { transition: { staggerChildren: reduced ? 0 : 0.05 } },
  };
  return { fadeInUp, stagger };
};

const TYPE_LABELS: Record<AnnouncementType, string> = {
  GENERAL: "General",
  NEW_PRODUCT: "New Product",
  SALE: "Sale",
  NEW_COLLECTION: "New Collection",
};

const TYPE_COLORS: Record<AnnouncementType, string> = {
  GENERAL: "bg-white/10 text-white/60 border-white/15",
  NEW_PRODUCT: "bg-blue-500/15 text-sky-300 border-blue-400/30",
  SALE: "bg-green-500/15 text-green-300 border-green-400/30",
  NEW_COLLECTION: "bg-indigo-500/15 text-indigo-300 border-indigo-400/30",
};

type Tab = "feed" | "following";

export default function FollowingPage() {
  const queryClient = useQueryClient();
  const { fadeInUp, stagger } = useAnims();
  const [tab, setTab] = useState<Tab>("feed");
  const [feedPage, setFeedPage] = useState(0);
  const [followingPage, setFollowingPage] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);

  const feedQuery = useQuery({
    queryKey: ["feed", "announcements", feedPage],
    queryFn: () => followApi.getFeed(feedPage).then((r) => r.data),
    enabled: tab === "feed",
  });

  const followingQuery = useQuery({
    queryKey: ["me", "following", followingPage],
    queryFn: () => followApi.listFollowing(followingPage).then((r) => r.data),
    enabled: tab === "following",
  });

  const bellMutation = useMutation({
    mutationFn: ({ companyId, enabled }: { companyId: string; enabled: boolean }) =>
      followApi.setNotifications(companyId, enabled),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["me", "following"] }),
  });

  const unfollowMutation = useMutation({
    mutationFn: (companyId: string) => followApi.unfollow(companyId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["me", "following"] }),
  });

  const feedItems = feedQuery.data?.items ?? [];
  const feedTotalPages = feedQuery.data?.totalPages ?? 0;
  const followedCompanies = followingQuery.data?.items ?? [];
  const followingTotalPages = followingQuery.data?.totalPages ?? 0;

  return (
    <div className="relative min-h-screen bg-slate-950 text-white">
      <NavyGridGlowBackground />

      <div className="mx-auto max-w-3xl px-4 pb-24 pt-28">
        {/* Header */}
        <motion.div variants={fadeInUp} initial="hidden" animate="visible" className="mb-8">
          <SectionTitle
            kicker="Your network"
            title="Following"
            subtitle="Announcements and companies you follow."
            theme="dark"
          />
        </motion.div>

        {/* Tabs */}
        <div className="flex gap-1 rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur p-1 mb-6">
          {(["feed", "following"] as Tab[]).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setTab(t)}
              className={`flex-1 flex items-center justify-center gap-2 text-sm font-medium rounded-xl px-4 py-2 transition-colors ${
                tab === t
                  ? "bg-blue-600 text-white"
                  : "text-white/55 hover:text-white hover:bg-white/[0.06]"
              }`}
            >
              {t === "feed" ? (
                <><Rss className="w-3.5 h-3.5" /> Announcement Feed</>
              ) : (
                <><Building2 className="w-3.5 h-3.5" /> Companies</>
              )}
            </button>
          ))}
        </div>

        {/* Feed tab */}
        {tab === "feed" && (
          <>
            {feedQuery.isLoading && (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-28 animate-pulse" />
                ))}
              </div>
            )}

            {!feedQuery.isLoading && feedItems.length === 0 && (
              <div className="flex flex-col items-center justify-center py-24 text-center">
                <Megaphone className="w-12 h-12 text-white/20 mb-4" />
                <h3 className="text-lg font-semibold text-white mb-2">No announcements yet</h3>
                <p className="text-sm text-white/50 max-w-xs">
                  Follow companies to see their latest news, sales, and product launches here.
                </p>
              </div>
            )}

            {!feedQuery.isLoading && feedItems.length > 0 && (
              <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-4">
                {feedItems.map((ann) => {
                  const type = ann.type as AnnouncementType;
                  const isExpanded = expanded === ann.id;
                  return (
                    <motion.article
                      key={ann.id}
                      variants={fadeInUp}
                      className="relative rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 shadow-sm"
                    >
                      <SectionGlow variant="a" />
                      {/* Company + meta */}
                      <div className="flex items-center gap-2.5 mb-3">
                        {ann.companyLogoUrl ? (
                          <img
                            src={ann.companyLogoUrl}
                            alt={ann.companyName}
                            className="w-7 h-7 rounded-lg object-cover border border-white/10 shrink-0"
                          />
                        ) : (
                          <div className="w-7 h-7 rounded-lg bg-blue-500/15 border border-white/10 flex items-center justify-center shrink-0">
                            <Building2 className="w-3.5 h-3.5 text-sky-300" />
                          </div>
                        )}
                        <Link
                          to={`/c/${ann.companyId}`}
                          className="text-sm font-medium text-white/80 hover:text-white transition-colors"
                        >
                          {ann.companyName}
                        </Link>
                        <span
                          className={`ml-auto text-xs font-semibold px-2.5 py-0.5 rounded-full border ${TYPE_COLORS[type]}`}
                        >
                          {TYPE_LABELS[type]}
                        </span>
                      </div>

                      {/* Content */}
                      <p className="text-sm font-semibold text-white mb-1.5 leading-snug">{ann.title}</p>
                      <p className={`text-xs text-white/60 leading-relaxed ${isExpanded ? "" : "line-clamp-3"}`}>
                        {ann.body}
                      </p>
                      {ann.body.length > 160 && (
                        <button
                          type="button"
                          onClick={() => setExpanded(isExpanded ? null : ann.id)}
                          className="mt-1 text-xs text-sky-400 hover:text-sky-300 transition-colors"
                        >
                          {isExpanded ? "Show less" : "Read more"}
                        </button>
                      )}

                      {/* Date */}
                      {ann.publishedAt && (
                        <p className="mt-3 text-xs text-white/35">
                          {new Date(ann.publishedAt).toLocaleDateString(undefined, {
                            month: "short", day: "numeric", year: "numeric",
                          })}
                        </p>
                      )}
                    </motion.article>
                  );
                })}
              </motion.div>
            )}

            {feedTotalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-8">
                <button
                  type="button"
                  disabled={feedPage === 0}
                  onClick={() => setFeedPage((p) => p - 1)}
                  className="text-sm border border-white/10 rounded-full px-4 py-1.5 text-white/60 hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  Previous
                </button>
                <span className="text-sm text-white/50">{feedPage + 1} / {feedTotalPages}</span>
                <button
                  type="button"
                  disabled={!feedQuery.data?.hasNext}
                  onClick={() => setFeedPage((p) => p + 1)}
                  className="text-sm border border-white/10 rounded-full px-4 py-1.5 text-white/60 hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}

        {/* Following tab */}
        {tab === "following" && (
          <>
            {followingQuery.isLoading && (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.04] h-20 animate-pulse" />
                ))}
              </div>
            )}

            {!followingQuery.isLoading && followedCompanies.length === 0 && (
              <div className="flex flex-col items-center justify-center py-24 text-center">
                <Building2 className="w-12 h-12 text-white/20 mb-4" />
                <h3 className="text-lg font-semibold text-white mb-2">Not following anyone yet</h3>
                <p className="text-sm text-white/50 max-w-xs">
                  Visit a company page and click Follow to start receiving their announcements.
                </p>
              </div>
            )}

            {!followingQuery.isLoading && followedCompanies.length > 0 && (
              <motion.div variants={stagger} initial="hidden" animate="visible" className="space-y-3">
                {followedCompanies.map((fc) => (
                  <motion.div
                    key={fc.followId}
                    variants={fadeInUp}
                    className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur px-5 py-4 flex items-center gap-4"
                  >
                    {fc.companyLogoUrl ? (
                      <img
                        src={fc.companyLogoUrl}
                        alt={fc.companyName}
                        className="w-10 h-10 rounded-xl object-cover border border-white/10 shrink-0"
                      />
                    ) : (
                      <div className="w-10 h-10 rounded-xl bg-blue-500/15 border border-white/10 flex items-center justify-center shrink-0">
                        <Building2 className="w-5 h-5 text-sky-300" />
                      </div>
                    )}

                    <div className="flex-1 min-w-0">
                      <Link
                        to={`/c/${fc.companyId}`}
                        className="text-sm font-semibold text-white hover:text-sky-200 transition-colors"
                      >
                        {fc.companyName}
                      </Link>
                      {fc.industry && (
                        <p className="text-xs text-white/45 mt-0.5">{fc.industry}</p>
                      )}
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        type="button"
                        title={fc.notificationsEnabled ? "Mute notifications" : "Enable notifications"}
                        onClick={() =>
                          bellMutation.mutate({
                            companyId: fc.companyId,
                            enabled: !fc.notificationsEnabled,
                          })
                        }
                        disabled={bellMutation.isPending}
                        className={`p-2 rounded-xl border transition-colors disabled:opacity-50 ${
                          fc.notificationsEnabled
                            ? "border-blue-500/30 bg-blue-600/10 text-sky-300 hover:bg-blue-600/20"
                            : "border-white/10 text-white/35 hover:text-white/60 hover:border-white/20"
                        }`}
                      >
                        {fc.notificationsEnabled ? (
                          <Bell className="w-3.5 h-3.5" />
                        ) : (
                          <BellOff className="w-3.5 h-3.5" />
                        )}
                      </button>
                      <button
                        type="button"
                        title="Unfollow"
                        onClick={() => unfollowMutation.mutate(fc.companyId)}
                        disabled={unfollowMutation.isPending}
                        className="p-2 rounded-xl border border-white/10 text-white/35 hover:text-red-400 hover:border-red-400/30 transition-colors disabled:opacity-50"
                      >
                        <UserMinus className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </motion.div>
                ))}
              </motion.div>
            )}

            {followingTotalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-8">
                <button
                  type="button"
                  disabled={followingPage === 0}
                  onClick={() => setFollowingPage((p) => p - 1)}
                  className="text-sm border border-white/10 rounded-full px-4 py-1.5 text-white/60 hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  Previous
                </button>
                <span className="text-sm text-white/50">{followingPage + 1} / {followingTotalPages}</span>
                <button
                  type="button"
                  disabled={!followingQuery.data?.hasNext}
                  onClick={() => setFollowingPage((p) => p + 1)}
                  className="text-sm border border-white/10 rounded-full px-4 py-1.5 text-white/60 hover:bg-white/[0.06] disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
