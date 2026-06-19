import { useReducedMotion, type TargetAndTransition, type Variants } from "framer-motion";

export function useAnims() {
  const prefersReducedMotion = useReducedMotion();

  const fadeInUp: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.25 } } }
    : { hidden: { opacity: 0, y: 18 }, visible: { opacity: 1, y: 0, transition: { duration: 0.5 } } };

  const fadeIn: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 } }
    : { hidden: { opacity: 0 }, visible: { opacity: 1, transition: { duration: 0.5 } } };

  const stagger: Variants = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.04 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.08 } } };

  const hoverLift: TargetAndTransition = prefersReducedMotion ? {} : { y: -4 };

  return { fadeInUp, fadeIn, stagger, hoverLift };
}
