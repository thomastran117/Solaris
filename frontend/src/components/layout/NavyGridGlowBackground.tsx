export default function NavyGridGlowBackground() {
  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="absolute inset-0 bg-slate-950" />
      <div className="absolute inset-0 bg-gradient-to-b from-slate-950 via-slate-950 to-slate-900" />
      <div className="absolute -top-40 left-1/2 h-[520px] w-[520px] -translate-x-1/2 rounded-full bg-blue-600/14 blur-[110px]" />
      <div className="absolute -bottom-56 left-[-18%] h-[640px] w-[640px] rounded-full bg-sky-400/10 blur-[130px]" />
      <div className="absolute top-[35%] right-[-18%] h-[520px] w-[520px] rounded-full bg-indigo-500/10 blur-[130px]" />
      <div
        className="
          absolute inset-0 opacity-[0.22]
          [background-image:linear-gradient(to_right,rgba(255,255,255,0.07)_1px,transparent_1px),linear-gradient(to_bottom,rgba(255,255,255,0.07)_1px,transparent_1px)]
          [background-size:44px_44px]
          [mask-image:radial-gradient(circle_at_top,black,transparent_72%)]
        "
      />
      <div
        className="
          absolute inset-0 opacity-[0.08]
          [background-image:linear-gradient(to_right,rgba(59,130,246,0.18)_1px,transparent_1px),linear-gradient(to_bottom,rgba(59,130,246,0.18)_1px,transparent_1px)]
          [background-size:14px_14px]
          [mask-image:radial-gradient(circle_at_center,black,transparent_75%)]
        "
      />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(255,255,255,0.035),transparent_55%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,transparent_40%,rgba(2,6,23,0.92))]" />
    </div>
  );
}
