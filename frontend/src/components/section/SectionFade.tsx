export default function SectionFade({
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
      <div className="absolute inset-0 bg-white/[0.02]" />
      {bottom ? (
        <div className="absolute bottom-0 left-0 right-0 h-24 bg-gradient-to-t from-slate-950/70 to-transparent" />
      ) : null}
    </div>
  );
}
