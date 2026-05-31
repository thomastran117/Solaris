import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  motion,
  useReducedMotion,
  useScroll,
  useSpring,
  useTransform,
  useInView,
} from "framer-motion";
import {
  Truck,
  Headset,
  DollarSign,
  ShieldCheck,
  RefreshCcw,
  Users,
  Sparkles,
  ThumbsUp,
  Shield,
  Search,
  Star,
  ArrowRight,
  BadgeCheck,
  ShoppingBag,
  CreditCard,
} from "lucide-react";

// ---- Animation helpers (respect reduced motion) ----
const useAnims = () => {
  const prefersReducedMotion = useReducedMotion();

  const fadeInUp = prefersReducedMotion
    ? {
        hidden: { opacity: 0 },
        visible: { opacity: 1, transition: { duration: 0.35 } },
      }
    : {
        hidden: { opacity: 0, y: 18 },
        visible: { opacity: 1, y: 0, transition: { duration: 0.55 } },
      };

  const fadeIn = prefersReducedMotion
    ? { hidden: { opacity: 0 }, visible: { opacity: 1 } }
    : {
        hidden: { opacity: 0 },
        visible: { opacity: 1, transition: { duration: 0.6 } },
      };

  const stagger = prefersReducedMotion
    ? { hidden: {}, visible: { transition: { staggerChildren: 0.05 } } }
    : { hidden: {}, visible: { transition: { staggerChildren: 0.12 } } };

  return { fadeInUp, fadeIn, stagger };
};

// ---- Data (commerce-oriented) ----
const services = [
  {
    title: "Fast Delivery",
    description: "Reliable shipping with live tracking updates.",
    icon: Truck,
  },
  {
    title: "24/7 Support",
    description: "We help you anytime—chat, email, or phone.",
    icon: Headset,
  },
  {
    title: "Secure Payments",
    description: "Protected checkout with modern encryption.",
    icon: ShieldCheck,
  },
  {
    title: "Easy Returns",
    description: "Simple returns & hassle-free refunds.",
    icon: RefreshCcw,
  },
  {
    title: "Great Prices",
    description: "Competitive pricing and frequent deals.",
    icon: DollarSign,
  },
  {
    title: "Trusted Community",
    description: "Thousands of verified customer reviews.",
    icon: Users,
  },
];

const categories = [
  { name: "Electronics", icon: CreditCard },
  { name: "Fashion", icon: Sparkles },
  { name: "Home & Kitchen", icon: ShoppingBag },
  { name: "Beauty", icon: ThumbsUp },
  { name: "Sports", icon: Shield },
  { name: "Best Sellers", icon: BadgeCheck },
];

// mock products – wire to API later
const featuredProducts = [
  {
    name: "Wireless Noise-Canceling Headphones",
    price: 129.99,
    rating: 4.6,
    tag: "Best Seller",
  },
  {
    name: "Smartwatch with Health Tracking",
    price: 89.0,
    rating: 4.4,
    tag: "Top Rated",
  },
  { name: "Ergonomic Office Chair", price: 199.99, rating: 4.7, tag: "Deal" },
  { name: "4K Streaming Stick", price: 39.99, rating: 4.5, tag: "Popular" },
  { name: "Air Fryer XL", price: 74.99, rating: 4.6, tag: "Recommended" },
  {
    name: "Portable Power Bank 20,000mAh",
    price: 34.99,
    rating: 4.3,
    tag: "Value",
  },
  { name: "Mechanical Keyboard", price: 59.99, rating: 4.5, tag: "Trending" },
  { name: "Skincare Starter Kit", price: 24.99, rating: 4.2, tag: "New" },
];

const reviews = [
  {
    name: "Alice",
    comment: "Fast shipping and the product quality was excellent.",
  },
  {
    name: "Bob",
    comment: "Checkout felt safe and customer support solved my issue quickly.",
  },
  { name: "Carol", comment: "Great prices—found what I needed in minutes." },
  {
    name: "Diego",
    comment: "Smooth experience overall. Returns were simple and quick.",
  },
];

const stats = [
  { label: "Orders Delivered", value: 1200000, display: "1.2M+" },
  { label: "Avg. Support Reply", value: 2, display: "< 2 min" },
  { label: "Satisfaction", value: 99.3, display: "99.3%" },
  { label: "Countries", value: 40, display: "40+" },
];

function NavyGridGlowBackground() {
  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      {/* Dark base first (prevents white fog) */}
      <div className="absolute inset-0 bg-slate-950" />
      <div className="absolute inset-0 bg-gradient-to-b from-slate-950 via-slate-950 to-slate-900" />

      {/* Glows (smaller + lower opacity + heavier blur) */}
      <div className="absolute -top-40 left-1/2 h-[520px] w-[520px] -translate-x-1/2 rounded-full bg-blue-600/14 blur-[110px]" />
      <div className="absolute -bottom-56 left-[-18%] h-[640px] w-[640px] rounded-full bg-sky-400/10 blur-[130px]" />
      <div className="absolute top-[35%] right-[-18%] h-[520px] w-[520px] rounded-full bg-indigo-500/10 blur-[130px]" />

      {/* Grid ABOVE glows (so it’s readable) */}
      <div
        className="
          absolute inset-0 opacity-[0.22]
          [background-image:linear-gradient(to_right,rgba(255,255,255,0.07)_1px,transparent_1px),linear-gradient(to_bottom,rgba(255,255,255,0.07)_1px,transparent_1px)]
          [background-size:44px_44px]
          [mask-image:radial-gradient(circle_at_top,black,transparent_72%)]
        "
      />

      {/* Micro texture (very subtle) */}
      <div
        className="
          absolute inset-0 opacity-[0.08]
          [background-image:linear-gradient(to_right,rgba(59,130,246,0.18)_1px,transparent_1px),linear-gradient(to_bottom,rgba(59,130,246,0.18)_1px,transparent_1px)]
          [background-size:14px_14px]
          [mask-image:radial-gradient(circle_at_center,black,transparent_75%)]
        "
      />

      {/* Softer highlight (less “white veil”) */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(255,255,255,0.035),transparent_55%)]" />

      {/* Vignette LAST (adds contrast, reduces glare) */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_40%,rgba(2,6,23,0.92))]" />
    </div>
  );
}

function SectionFade({
  top = true,
  bottom = true,
}: {
  top?: boolean;
  bottom?: boolean;
}) {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
      {top ? (
        <div className="absolute top-0 left-0 right-0 h-24 bg-gradient-to-b from-slate-950/70 to-transparent" />
      ) : null}

      {/* subtle film so sections feel connected */}
      <div className="absolute inset-0 bg-white/[0.02]" />

      {bottom ? (
        <div className="absolute bottom-0 left-0 right-0 h-24 bg-gradient-to-t from-slate-950/70 to-transparent" />
      ) : null}
    </div>
  );
}

function SectionGlow({
  variant = "a",
}: {
  variant?: "a" | "b" | "c";
}) {
  const map = {
    a: (
      <>
        <div className="absolute -top-24 -left-24 h-72 w-72 rounded-full bg-blue-500/12 blur-[80px]" />
        <div className="absolute -bottom-28 right-[-120px] h-80 w-80 rounded-full bg-sky-400/10 blur-[90px]" />
      </>
    ),
    b: (
      <>
        <div className="absolute -top-24 right-[-120px] h-80 w-80 rounded-full bg-indigo-500/10 blur-[90px]" />
        <div className="absolute -bottom-24 -left-24 h-72 w-72 rounded-full bg-blue-600/10 blur-[85px]" />
      </>
    ),
    c: (
      <>
        <div className="absolute top-10 left-[-140px] h-96 w-96 rounded-full bg-sky-400/10 blur-[95px]" />
        <div className="absolute bottom-[-160px] right-[-160px] h-[28rem] w-[28rem] rounded-full bg-blue-600/10 blur-[110px]" />
      </>
    ),
  };

  return (
    <div aria-hidden className="absolute inset-0 -z-10 overflow-hidden">
      {/* soft local vignette */}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(255,255,255,0.06),transparent_60%)]" />
      {map[variant]}
    </div>
  );
}

// ---- Small UI components ----
function SectionTitle({
  kicker,
  title,
  subtitle,
  align = "center",
  theme = "light",
}: {
  kicker?: string;
  title: string;
  subtitle?: string;
  align?: "center" | "left";
  theme?: "light" | "dark";
}) {
  const isDark = theme === "dark";
  return (
    <div
      className={`max-w-3xl mx-auto ${
        align === "left" ? "text-left" : "text-center"
      }`}
    >
      {kicker ? (
        <p
          className={`text-xs uppercase tracking-[0.25em] font-semibold mb-2 ${
            isDark ? "text-sky-200/90" : "text-blue-700"
          }`}
        >
          {kicker}
        </p>
      ) : null}

      <h2
        className={`text-3xl md:text-4xl font-extrabold mb-3 ${
          isDark ? "text-white" : "text-slate-950"
        }`}
      >
        {title}
      </h2>

      {subtitle ? (
        <p className={`text-lg ${isDark ? "text-white/70" : "text-slate-700"}`}>
          {subtitle}
        </p>
      ) : null}
    </div>
  );
}

function Pill({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center rounded-full border border-white/15 bg-white/10 px-3 py-1 text-xs text-white/90">
      {children}
    </span>
  );
}

function RatingStars({ rating }: { rating: number }) {
  const full = Math.floor(rating);
  const hasHalf = rating - full >= 0.5;

  return (
    <div className="flex items-center gap-1">
      {Array.from({ length: 5 }).map((_, i) => {
        const filled = i < full || (i === full && hasHalf);
        return (
          <Star
            key={i}
            className={`h-4 w-4 ${
              filled ? "text-blue-400 fill-blue-400" : "text-white/25"
            }`}
          />
        );
      })}
      <span className="text-xs text-white/60 ml-1">{rating.toFixed(1)}</span>
    </div>
  );
}

function FeatureCard({
  Icon,
  title,
  description,
}: {
  Icon: any;
  title: string;
  description: string;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const isInView = useInView(ref, { once: false, margin: "-10% 0px -10% 0px" });
  const { scrollY } = useScroll();
  const y = useTransform(scrollY, [0, 700], [0, -6]);

  return (
    <motion.div
      ref={ref}
      style={{ y }}
      whileHover={{ scale: 1.015, translateY: -3 }}
      className="
        rounded-2xl border border-white/10
        bg-white/[0.06] backdrop-blur
        p-6 shadow-sm hover:shadow-md
        transition-transform duration-300
      "
      animate={{
        boxShadow: isInView
          ? "0 16px 36px rgba(2,6,23,0.40)"
          : "0 6px 14px rgba(2,6,23,0.25)",
      }}
      transition={{ type: "spring", stiffness: 120, damping: 18 }}
    >
      <div className="inline-flex items-center justify-center rounded-xl bg-blue-500/15 p-3 mb-4 border border-white/10">
        <Icon aria-hidden className="h-6 w-6 text-sky-200" />
      </div>

      <h3 className="text-lg font-semibold text-white mb-1">{title}</h3>
      <p className="text-sm text-white/70 leading-relaxed">{description}</p>
    </motion.div>
  );
}

function CountUp({
  to,
  prefix = "",
  suffix = "",
}: {
  to: number;
  prefix?: string;
  suffix?: string;
}) {
  const ref = useRef<HTMLSpanElement | null>(null);
  const inView = useInView(ref, { once: true, margin: "-10% 0px -10% 0px" });
  const [val, setVal] = useState(0);

  useEffect(() => {
    if (!inView) return;
    const duration = 900;
    const start = performance.now();
    const from = 0;

    const animate = (t: number) => {
      const p = Math.min(1, (t - start) / duration);
      const eased = 1 - Math.pow(1 - p, 3);
      setVal(Math.floor(from + (to - from) * eased));
      if (p < 1) requestAnimationFrame(animate);
    };

    const r = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(r);
  }, [inView, to]);

  return (
    <span ref={ref}>
      {prefix}
      {val.toLocaleString()}
      {suffix}
    </span>
  );
}

function Stat({
  value,
  label,
  display,
}: {
  value: number;
  label: string;
  display: string;
}) {
  const isPercent = /%$/.test(String(display));

  return (
    <div className="text-center">
      <div className="text-3xl md:text-4xl font-extrabold text-white">
        {/[\+%]/.test(display) ? (
          <>
            <CountUp to={Math.round(value)} />
            {display.includes("+") ? "+" : ""}
            {isPercent ? "%" : ""}
          </>
        ) : (
          display
        )}
      </div>
      <div className="text-sm text-white/65 mt-1">{label}</div>
    </div>
  );
}

function Testimonial({ name, comment }: { name: string; comment: string }) {
  return (
    <figure className="rounded-2xl border border-white/10 bg-white/[0.06] backdrop-blur p-5 shadow-sm">
      <blockquote className="text-white/80 italic leading-relaxed">
        “{comment}”
      </blockquote>
      <figcaption className="mt-3 text-right text-sm font-medium text-white/65">
        — {name}
      </figcaption>
    </figure>
  );
}

function ScrollProgressBar() {
  const { scrollYProgress } = useScroll();
  const scaleX = useSpring(scrollYProgress, {
    stiffness: 120,
    damping: 24,
    mass: 0.2,
  });

  return (
    <motion.div
      aria-hidden
      style={{ scaleX }}
      className="fixed left-0 right-0 top-0 h-1 origin-left bg-blue-500 z-50"
    />
  );
}

function ParallaxBlobs() {
  const ref = useRef<HTMLDivElement | null>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start start", "end start"],
  });
  const y1 = useTransform(scrollYProgress, [0, 1], [0, 90]);
  const y2 = useTransform(scrollYProgress, [0, 1], [0, -70]);

  return (
    <div ref={ref} className="absolute inset-0 -z-10">
      <motion.div
        style={{ y: y1 }}
        className="absolute top-[-7rem] left-[-7rem] w-80 h-80 rounded-full bg-blue-700/30 blur-[90px]"
      />
      <motion.div
        style={{ y: y2 }}
        className="absolute bottom-[-9rem] right-[-7rem] w-[28rem] h-[28rem] rounded-full bg-sky-400/18 blur-[95px]"
      />
      <div className="absolute inset-0 bg-gradient-to-b from-slate-950 via-slate-950/90 to-slate-950/70" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(59,130,246,0.18),transparent_55%)]" />
    </div>
  );
}

function ProductCard({
  name,
  price,
  rating,
  tag,
}: {
  name: string;
  price: number;
  rating: number;
  tag: string;
}) {
  return (
    <motion.a
      href="#"
      whileHover={{ y: -4 }}
      className="
        group block overflow-hidden rounded-2xl
        border border-white/10 bg-white/[0.06]
        backdrop-blur shadow-sm hover:shadow-md transition
      "
    >
      <div className="relative">
        <div className="h-40 w-full bg-gradient-to-br from-white/10 to-white/0" />
        <div className="absolute left-4 top-4">
          <span className="text-xs font-semibold rounded-full bg-white/10 text-white px-3 py-1 border border-white/10">
            {tag}
          </span>
        </div>
      </div>

      <div className="p-5">
        <h3 className="font-semibold text-white leading-snug line-clamp-2">
          {name}
        </h3>

        <div className="mt-2">
          <RatingStars rating={rating} />
        </div>

        <div className="mt-3 flex items-center justify-between">
          <p className="text-lg font-extrabold text-white">
            ${price.toFixed(2)}
          </p>
          <span className="text-sm text-sky-200 font-semibold group-hover:translate-x-0.5 transition">
            View <ArrowRight className="inline h-4 w-4" />
          </span>
        </div>
      </div>
    </motion.a>
  );
}

function CategoryTile({ Icon, name }: { Icon: any; name: string }) {
  return (
    <motion.a
      href="#"
      whileHover={{ y: -3 }}
      className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/5 hover:bg-white/10 px-4 py-4 transition"
    >
      <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-blue-500/15 border border-white/10">
        <Icon className="h-5 w-5 text-sky-200" />
      </span>
      <span className="font-semibold text-white">{name}</span>
    </motion.a>
  );
}

// ---- Page ----
export default function Home() {
  const { fadeInUp, fadeIn, stagger } = useAnims();

  const heroRef = useRef<HTMLElement | null>(null);
  const { scrollY } = useScroll({
    target: heroRef,
    offset: ["start start", "end start"],
  });
  const heroTranslateY = useTransform(scrollY, [0, 320], [0, -28]);
  const heroOpacity = useTransform(scrollY, [0, 320], [1, 0.86]);

  const [query, setQuery] = useState("");

  const filteredProducts = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return featuredProducts;
    return featuredProducts.filter((p) => p.name.toLowerCase().includes(q));
  }, [query]);

  return (
    <main className="relative min-h-screen text-white">
      <NavyGridGlowBackground />
      <ScrollProgressBar />

      {/* TOP STRIP */}
      <div className="relative border-b border-white/10 bg-white/[0.04] backdrop-blur">
        <div className="mx-auto max-w-6xl px-6 py-2 flex items-center justify-between text-xs">
          <span className="text-white/80">
            Free shipping on orders over $35 • Easy returns
          </span>
          <span className="text-white/70">Secure checkout • 24/7 support</span>
        </div>
      </div>

      {/* HERO */}
      <section
        ref={heroRef as any}
        aria-label="Hero"
        className="relative isolate overflow-hidden"
      >
        <ParallaxBlobs />

        <motion.div
          style={{ y: heroTranslateY, opacity: heroOpacity }}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.45 }}
          variants={fadeInUp}
          className="mx-auto max-w-6xl px-6 pt-16 pb-14"
        >
          <div className="flex flex-col gap-10">
            <div className="flex flex-col items-center text-center">
              <div className="flex flex-wrap items-center justify-center gap-2">
                <Pill>Deals updated daily</Pill>
                <Pill>Fast delivery</Pill>
                <Pill>Secure payments</Pill>
              </div>

              <h1 className="mt-6 text-4xl md:text-6xl font-extrabold tracking-tight">
                Shop smarter with a{" "}
                <span className="text-transparent bg-clip-text bg-gradient-to-r from-sky-200 to-blue-400">
                  fast, secure
                </span>{" "}
                marketplace
              </h1>

              <p className="mt-5 text-lg md:text-xl max-w-2xl text-white/80">
                Discover popular products, trending categories, and reliable
                delivery—built for an Amazon-like e-commerce experience.
              </p>

              {/* Search bar CTA */}
              <div className="mt-8 w-full max-w-2xl">
                <div className="flex items-center gap-2 rounded-2xl border border-white/10 bg-white/[0.06] p-2 backdrop-blur">
                  <div className="pl-3 text-white/70">
                    <Search className="h-5 w-5" />
                  </div>
                  <input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Search products, brands, and deals..."
                    className="w-full bg-transparent outline-none text-white placeholder:text-white/45 px-2 py-3"
                  />
                  <a
                    href="#featured"
                    className="shrink-0 rounded-xl bg-blue-600 hover:bg-blue-500 px-5 py-3 font-semibold shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-white/60"
                  >
                    Search
                  </a>
                </div>
                <p className="mt-3 text-xs text-white/60">
                  Tip: try “headphones”, “keyboard”, or “air fryer”.
                </p>
              </div>
            </div>

            {/* Category tiles */}
            <motion.div
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, amount: 0.25 }}
              variants={stagger}
              className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3"
            >
              {categories.map((c) => (
                <motion.div key={c.name} variants={fadeInUp}>
                  <CategoryTile Icon={c.icon} name={c.name} />
                </motion.div>
              ))}
            </motion.div>

            {/* trust mini row */}
            <motion.div
              variants={fadeIn}
              className="grid grid-cols-2 md:grid-cols-4 gap-4"
            >
              {[
                { k: "Buyer Protection", v: "30-day guarantee" },
                { k: "Shipping", v: "Fast & tracked" },
                { k: "Payments", v: "Encrypted checkout" },
                { k: "Support", v: "24/7 assistance" },
              ].map((x) => (
                <div
                  key={x.k}
                  className="rounded-2xl border border-white/10 bg-white/[0.06] px-4 py-4 backdrop-blur"
                >
                  <div className="text-xs text-white/60">{x.k}</div>
                  <div className="mt-1 font-semibold text-white">{x.v}</div>
                </div>
              ))}
            </motion.div>
          </div>
        </motion.div>
      </section>

      {/* DISCOVER / STATS (was white, now glass + glow) */}
      <section
        aria-label="Discover"
        className="relative py-16 px-6 border-y border-white/10 bg-white/[0.04] backdrop-blur"
      >
        <SectionFade />
        <SectionGlow variant="a" />
        <SectionTitle
          theme="dark"
          kicker="Performance"
          title="Built for speed, reliability, and trust"
          subtitle="A commerce experience users can rely on—from browsing to checkout."
        />
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, amount: 0.3 }}
          variants={stagger}
          className="mt-10 grid grid-cols-2 md:grid-cols-4 gap-6 max-w-5xl mx-auto"
        >
          {stats.map((s, i) => (
            <motion.div key={i} variants={fadeInUp}>
              <Stat value={s.value} label={s.label} display={s.display} />
            </motion.div>
          ))}
        </motion.div>
      </section>

      {/* FEATURED PRODUCTS */}
      <section
        id="featured"
        aria-label="Featured products"
        className="relative py-20 px-6"
      >
        <SectionFade />
        <SectionGlow variant="b" />
        <div className="max-w-6xl mx-auto">
          <SectionTitle
            theme="dark"
            kicker="Featured"
            title="Popular picks right now"
            subtitle="Curated products customers are buying today."
            align="left"
          />

          <div className="mt-10 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {filteredProducts.slice(0, 8).map((p) => (
              <ProductCard
                key={p.name}
                name={p.name}
                price={p.price}
                rating={p.rating}
                tag={p.tag}
              />
            ))}
          </div>

          <div className="mt-10 flex items-center justify-center">
            <a
              href="#"
              className="
                inline-flex items-center gap-2 rounded-full
                bg-white/10 hover:bg-white/15 border border-white/10
                text-white px-7 py-3 font-semibold transition shadow-sm backdrop-blur
              "
            >
              Browse all products <ArrowRight className="h-4 w-4" />
            </a>
          </div>
        </div>
      </section>

      {/* SERVICES */}
      <section
        aria-label="Services"
        className="relative py-20 px-6 border-y border-white/10 bg-white/[0.04] backdrop-blur"
      >
        <SectionFade />
        <SectionGlow variant="c" />
        <SectionTitle
          theme="dark"
          kicker="Why shop here"
          title="Everything you expect from a modern marketplace"
          subtitle="Fast delivery, secure checkout, and customer-first policies."
        />
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: false, amount: 0.2 }}
          variants={stagger}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 mt-12 max-w-6xl mx-auto"
        >
          {services.map((s, i) => (
            <motion.div key={i} variants={fadeInUp}>
              <FeatureCard Icon={s.icon} title={s.title} description={s.description} />
            </motion.div>
          ))}
        </motion.div>
      </section>

      {/* WHY US */}
      <section aria-label="Why choose us" className="relative py-20 px-6">
        <SectionFade />
        <SectionGlow variant="a" />
        <SectionTitle
          theme="dark"
          title="Trusted commerce, end-to-end"
          subtitle="From product discovery to delivery."
        />

        <div className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6 mt-10">
          {[
            {
              Icon: Sparkles,
              t: "Better discovery",
              d: "Find what you want faster with curated categories and featured deals.",
            },
            {
              Icon: BadgeCheck,
              t: "Verified trust",
              d: "Clear policies, secure payments, and transparent order tracking.",
            },
            {
              Icon: Shield,
              t: "Safety by default",
              d: "Modern security practices to protect your account and checkout.",
            },
          ].map((item, idx) => (
            <motion.div
              key={idx}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.5, delay: idx * 0.05 }}
              className="
                rounded-2xl border border-white/10 bg-white/[0.06]
                backdrop-blur p-7 shadow-sm hover:shadow-md transition
              "
            >
              <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-500/15 border border-white/10">
                <item.Icon className="h-6 w-6 text-sky-200" />
              </div>

              <h3 className="mt-4 font-semibold text-lg text-white">{item.t}</h3>
              <p className="mt-2 text-sm text-white/70 leading-relaxed">{item.d}</p>
            </motion.div>
          ))}
        </div>
      </section>

      {/* REVIEWS */}
      <section
        aria-label="Customer reviews"
        className="relative py-20 px-6 border-y border-white/10 bg-white/[0.04] backdrop-blur"
      >
        <SectionFade />
        <SectionGlow variant="b" />
        <div className="max-w-6xl mx-auto grid md:grid-cols-2 gap-10 items-center">
          <motion.div
            initial={{ opacity: 0, scale: 0.98 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.6 }}
            className="
              rounded-3xl border border-white/10
              bg-white/[0.06] backdrop-blur
              p-8 shadow-sm
            "
          >
            <p className="text-xs uppercase tracking-[0.25em] text-sky-200/90 font-semibold">
              Customer stories
            </p>
            <h3 className="mt-2 text-2xl font-extrabold text-white">
              People love the experience
            </h3>
            <p className="mt-3 text-white/70">
              Smooth browsing, safe checkout, and dependable delivery—built like
              a modern Amazon-style storefront.
            </p>

            <div className="mt-6 grid grid-cols-2 gap-4">
              {[
                { k: "Shipping", v: "Fast & tracked" },
                { k: "Returns", v: "30-day window" },
                { k: "Support", v: "24/7 help" },
                { k: "Checkout", v: "Encrypted" },
              ].map((x) => (
                <div
                  key={x.k}
                  className="rounded-2xl bg-white/[0.06] border border-white/10 p-4"
                >
                  <div className="text-xs text-white/60">{x.k}</div>
                  <div className="mt-1 font-semibold text-white">{x.v}</div>
                </div>
              ))}
            </div>
          </motion.div>

          <div>
            <SectionTitle
              theme="dark"
              align="left"
              title="What customers say"
              subtitle="Real feedback from real buyers."
            />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-6">
              {reviews.map((r, i) => (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, y: 12 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.4 }}
                >
                  <Testimonial name={r.name} comment={r.comment} />
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section aria-label="FAQ" className="relative py-20 px-6">
        <SectionFade />
        <SectionGlow variant="c" />
        <SectionTitle
          theme="dark"
          title="Frequently asked questions"
          subtitle="Quick answers before you buy."
        />

        <div className="max-w-4xl mx-auto mt-8 space-y-4">
          {[
            {
              q: "Can I cancel anytime?",
              a: "Yes. You can cancel subscriptions or memberships anytime from your account settings.",
            },
            {
              q: "Do you offer refunds?",
              a: "Yes. Eligible items can be returned within 30 days. Refunds are processed quickly once received.",
            },
            {
              q: "Is checkout secure?",
              a: "Yes. We use encryption and modern security best practices to protect your payment and account data.",
            },
            {
              q: "How do I track my order?",
              a: "After purchase, you’ll see live tracking updates on your Orders page.",
            },
          ].map((item, i) => (
            <motion.details
              key={i}
              className="group rounded-2xl p-5 border border-white/10 bg-white/[0.06] backdrop-blur shadow-sm"
              initial={{ opacity: 0, scale: 0.985 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true }}
              transition={{ duration: 0.35 }}
            >
              <summary className="cursor-pointer list-none select-none flex items-center justify-between text-left">
                <span className="font-semibold text-white">{item.q}</span>
                <span className="ml-4 text-sky-200 group-open:rotate-180 transition-transform">
                  ▾
                </span>
              </summary>
              <p className="mt-3 text-white/70 leading-relaxed">{item.a}</p>
            </motion.details>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section aria-label="Call to action" className="relative py-20 px-6">
        <SectionFade />
        <SectionGlow variant="b" />

        <div className="max-w-6xl mx-auto grid md:grid-cols-2 gap-10 items-center">
          <div>
            <h2 className="text-3xl md:text-4xl font-extrabold text-white">
              Ready to start shopping?
            </h2>
            <p className="text-lg mt-3 text-white/80 max-w-xl">
              Browse best sellers, discover deals, and checkout confidently with
              secure payments and easy returns.
            </p>

            <div className="mt-7 flex flex-wrap items-center gap-3">
              <a
                href="#featured"
                className="rounded-full bg-blue-600 hover:bg-blue-500 text-white font-bold px-8 py-3 shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
              >
                Shop featured
              </a>
              <a
                href="#"
                className="rounded-full border border-white/20 px-8 py-3 font-semibold text-white/90 hover:bg-white/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/70"
              >
                View categories
              </a>
            </div>

            <p className="mt-5 text-sm text-white/60">
              Free shipping • Easy returns • Secure checkout
            </p>
          </div>

          <div className="rounded-3xl border border-white/10 bg-white/[0.06] p-8 backdrop-blur shadow-sm">
            <p className="text-xs uppercase tracking-[0.25em] text-white/70">
              Today’s deal
            </p>
            <h3 className="mt-2 text-2xl font-extrabold text-white">
              Save up to 30% on best sellers
            </h3>
            <p className="mt-2 text-white/75">
              Limited-time discounts across electronics, home, and accessories.
            </p>

            <div className="mt-6 grid grid-cols-2 gap-4">
              {[
                { Icon: ShieldCheck, t: "Secure checkout" },
                { Icon: Truck, t: "Fast delivery" },
                { Icon: RefreshCcw, t: "Easy returns" },
                { Icon: Headset, t: "24/7 support" },
              ].map((x) => (
                <div
                  key={x.t}
                  className="flex items-center gap-3 rounded-2xl bg-white/[0.06] border border-white/10 px-4 py-3"
                >
                  <x.Icon className="h-5 w-5 text-sky-200" />
                  <span className="text-sm font-semibold text-white">
                    {x.t}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* bottom spacing */}
      <div className="h-12" />
    </main>
  );
}
