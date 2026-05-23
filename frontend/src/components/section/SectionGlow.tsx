export default function SectionGlow({ variant = "a" }: { variant?: "a" | "b" | "c" }) {
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
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(255,255,255,0.06),transparent_60%)]" />
      {map[variant]}
    </div>
  );
}
