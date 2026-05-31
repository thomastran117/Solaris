import { useState } from "react";
import { motion, type Variants } from "framer-motion";
import { ChevronDown, ChevronUp } from "lucide-react";
import AnswerCard from "./AnswerCard";
import type { Question } from "../../types/qa";

interface Props {
  question: Question;
  productId: string;
  fadeInUp: Variants;
  stagger: Variants;
}

export default function QuestionCard({ question, productId, fadeInUp, stagger }: Props) {
  const [expanded, setExpanded] = useState(true);
  const askerName = `${question.askerFirstName} ${question.askerLastName}`.trim();
  const date = new Date(question.createdAt).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  return (
    <motion.div
      variants={fadeInUp}
      className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 space-y-3 hover:shadow-md transition-shadow"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-semibold text-white leading-relaxed flex-1">
          Q: {question.questionText}
        </p>
        {question.answers.length > 0 && (
          <button
            onClick={() => setExpanded((v) => !v)}
            className="flex-shrink-0 text-white/40 hover:text-white/70 transition-colors"
            aria-label={expanded ? "Collapse answers" : "Expand answers"}
          >
            {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
        )}
      </div>

      <div className="flex items-center gap-2 text-xs text-white/45">
        <span>{askerName}</span>
        <span>&middot;</span>
        <span>{date}</span>
        <span>&middot;</span>
        <span>{question.answers.length} answer{question.answers.length !== 1 ? "s" : ""}</span>
      </div>

      {expanded && question.answers.length > 0 && (
        <motion.div
          variants={stagger}
          initial="hidden"
          animate="visible"
          className="space-y-3 pt-1"
        >
          {question.answers.map((answer) => (
            <AnswerCard
              key={answer.id}
              answer={answer}
              questionId={question.id}
              productId={productId}
              variants={fadeInUp}
            />
          ))}
        </motion.div>
      )}

      {expanded && question.answers.length === 0 && (
        <p className="text-xs text-white/40 ml-6">No answers yet. Be the first to answer.</p>
      )}
    </motion.div>
  );
}
