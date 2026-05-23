export default function SectionTitle({
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
    <div className={`max-w-3xl mx-auto ${align === "left" ? "text-left" : "text-center"}`}>
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
