import { motion, type Variants } from "framer-motion";
import UpvoteButton from "./UpvoteButton";
import type { Answer } from "../../types/qa";

interface Props {
  answer: Answer;
  questionId: string;
  productId: string;
  variants: Variants;
}

export default function AnswerCard({ answer, questionId, productId, variants }: Props) {
  const answererName = `${answer.answererFirstName} ${answer.answererLastName}`.trim();
  const date = new Date(answer.createdAt).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  return (
    <motion.div
      variants={variants}
      className="ml-6 border-l-2 border-white/10 pl-4"
    >
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-white/80 leading-relaxed flex-1">{answer.answerText}</p>
        <UpvoteButton
          questionId={questionId}
          answerId={answer.id}
          upvoteCount={answer.upvoteCount}
          productId={productId}
        />
      </div>
      <div className="flex items-center gap-2 mt-2">
        <span className="text-xs text-white/50">{answererName}</span>
        {answer.vendorAnswer && (
          <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-sky-400/15 border border-sky-400/30 text-sky-200">
            Vendor
          </span>
        )}
        <span className="text-xs text-white/35">{date}</span>
      </div>
    </motion.div>
  );
}
