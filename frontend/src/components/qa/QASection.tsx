import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSelector } from "react-redux";
import { motion } from "framer-motion";
import { MessageCircleQuestion } from "lucide-react";
import { qaApi } from "../../api/qa";
import type { RootState } from "../../stores";
import QuestionCard from "./QuestionCard";
import AskQuestionForm from "./AskQuestionForm";
import { useAnims } from "../../hooks/useAnims";

interface Props {
  productId: string;
}


export default function QASection({ productId }: Props) {
  const { fadeInUp, stagger } = useAnims();
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  const authenticated = !!accessToken;

  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useQuery({
    queryKey: ["qa", "questions", productId, page],
    queryFn: () => qaApi.listQuestions(productId, { page, size: 10 }).then((r) => r.data),
    enabled: !!productId,
  });

  return (
    <section className="py-16 bg-white/[0.04] backdrop-blur border-y border-white/10">
      <div className="mx-auto max-w-6xl px-4 space-y-8">
        {/* Header */}
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-10 h-10 rounded-xl bg-blue-500/15 border border-white/10">
            <MessageCircleQuestion className="w-5 h-5 text-sky-200" />
          </div>
          <div>
            <p className="text-xs uppercase tracking-[0.25em] font-semibold text-sky-200/90">
              Community
            </p>
            <h2 className="text-2xl font-extrabold text-white">Questions &amp; Answers</h2>
          </div>
        </div>

        {/* Ask a question */}
        {authenticated ? (
          <div className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5">
            <p className="text-sm font-semibold text-white mb-3">Ask a Question</p>
            <AskQuestionForm productId={productId} />
          </div>
        ) : (
          <p className="text-sm text-white/50">
            <a href="/login" className="text-sky-200 hover:underline">Sign in</a> to ask a question.
          </p>
        )}

        {/* Question list */}
        {isLoading && (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="rounded-2xl border border-white/10 bg-white/[0.06] h-24 animate-pulse" />
            ))}
          </div>
        )}

        {isError && (
          <p className="text-sm text-red-400">Failed to load questions. Please try again.</p>
        )}

        {data && data.items.length === 0 && !isLoading && (
          <div className="text-center py-12">
            <p className="text-white/50 text-sm">No questions yet. Be the first to ask!</p>
          </div>
        )}

        {data && data.items.length > 0 && (
          <motion.div
            variants={stagger}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, amount: 0.1 }}
            className="space-y-4"
          >
            {data.items.map((question) => (
              <QuestionCard
                key={question.id}
                question={question}
                productId={productId}
                fadeInUp={fadeInUp}
                stagger={stagger}
              />
            ))}
          </motion.div>
        )}

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-center gap-3">
            <button
              disabled={!data.hasPrevious}
              onClick={() => setPage((p) => p - 1)}
              className="px-4 py-2 rounded-full border border-white/20 hover:bg-white/10 text-sm text-white/70 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Previous
            </button>
            <span className="text-sm text-white/50">
              Page {data.page + 1} of {data.totalPages}
            </span>
            <button
              disabled={!data.hasNext}
              onClick={() => setPage((p) => p + 1)}
              className="px-4 py-2 rounded-full border border-white/20 hover:bg-white/10 text-sm text-white/70 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </section>
  );
}
