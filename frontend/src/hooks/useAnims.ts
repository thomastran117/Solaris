import {
  useReducedMotion,
  type TargetAndTransition,
  type Variants,
} from "framer-motion";
import { useHydrated } from "./useHydrated";

// Before hydration, "hidden" equals "visible": server-rendered HTML must show its
// content without JavaScript (and hydrate without a style mismatch). Framer Motion
// applies `initial` only on mount, so switching to the animated variants afterwards
// leaves already-visible content alone and animates only content mounted later.
const SSR_VISIBLE: Variants = {
  hidden: { opacity: 1, y: 0 },
  visible: { opacity: 1, y: 0 },
};
const SSR_STAGGER: Variants = { hidden: {}, visible: {} };

export function useAnims() {
  const prefersReducedMotion = useReducedMotion();
  const hydrated = useHydrated();

  // whileHover never affects server markup, so it does not depend on hydration.
  const hoverLift: TargetAndTransition = prefersReducedMotion ? {} : { y: -4 };

  if (!hydrated) {
    return {
      fadeInUp: SSR_VISIBLE,
      fadeIn: SSR_VISIBLE,
      stagger: SSR_STAGGER,
      hoverLift,
    };
  }

  const fadeInUp: Variants = prefersReducedMotion
    ? {
        hidden: { opacity: 0 },
        visible: { opacity: 1, transition: { duration: 0.25 } },
      }
    : {
        hidden: { opacity: 0, y: 18 },
        visible: { opacity: 1, y: 0, transition: { duration: 0.5 } },
      };

  const fadeIn: Variants = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 } }
    : {
        hidden: { opacity: 0 },
        visible: { opacity: 1, transition: { duration: 0.5 } },
      };

  const stagger: Variants = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.04 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.08 } } };

  return { fadeInUp, fadeIn, stagger, hoverLift };
}

/**
 * For inline `initial={{ ... }}` objects: returns `false` (start in the final state)
 * until hydration has finished, otherwise the given initial state.
 */
export function useEntranceInitial<T extends TargetAndTransition>(
  initial: T
): T | false {
  return useHydrated() ? initial : false;
}
