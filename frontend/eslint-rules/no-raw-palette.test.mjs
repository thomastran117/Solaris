import { RuleTester } from "eslint";
import rule from "./no-raw-palette.mjs";

// RuleTester drives Vitest's global describe/it (globals: true in vitest.config).
const tester = new RuleTester({
  languageOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
    parserOptions: { ecmaFeatures: { jsx: true } },
  },
});

tester.run("no-raw-palette", rule, {
  valid: [
    'const c = "bg-surface text-foreground border-border";',
    'const c = "text-muted-foreground hover:bg-muted";',
    'const c = "bg-primary text-primary-foreground ring-ring";',
    'const c = "text-danger bg-danger-subtle";',
    'const c = "prose prose-zinc dark:prose-invert";',
    'const c = "border-2 border-transparent text-sm";',
    'const href = "#";',
    'const anchor = "#reviews";',
    'const stroke = "var(--chart-1)";',
    {
      code: 'const c = "backdrop-blur supports-[backdrop-filter]:bg-background/60";',
      options: [{ allowBlur: true }],
    },
    'const s = "textual whiteboard";',
  ],
  invalid: [
    {
      code: 'const c = "text-white";',
      errors: [{ messageId: "palette", data: { value: "text-white" } }],
    },
    {
      code: 'const c = "rounded-xl border border-white/10 bg-white/[0.04]";',
      errors: [
        { messageId: "palette", data: { value: "border-white" } },
        { messageId: "palette", data: { value: "bg-white" } },
      ],
    },
    {
      code: 'const c = "hover:bg-blue-500 focus:ring-sky-400/40";',
      errors: [
        { messageId: "palette", data: { value: "bg-blue-500" } },
        { messageId: "palette", data: { value: "ring-sky-400" } },
      ],
    },
    {
      code: 'const c = "bg-gradient-to-r from-[#0b1221]/95 via-indigo-500";',
      errors: [
        { messageId: "palette", data: { value: "from-[#0b1221]" } },
        { messageId: "palette", data: { value: "via-indigo-500" } },
      ],
    },
    {
      code: 'const c = `px-4 ${active ? "text-sky-200" : ""} bg-slate-950`;',
      errors: [
        { messageId: "palette", data: { value: "text-sky-200" } },
        { messageId: "palette", data: { value: "bg-slate-950" } },
      ],
    },
    {
      code: 'const c = <div className="md:dark:text-gray-300" />;',
      errors: [{ messageId: "palette", data: { value: "text-gray-300" } }],
    },
    {
      code: 'const c = "bg-surface backdrop-blur-sm";',
      errors: [{ messageId: "blur", data: { value: "backdrop-blur-sm" } }],
    },
    {
      code: 'const color = "#38bdf8";',
      errors: [{ messageId: "hex", data: { value: "#38bdf8" } }],
    },
  ],
});
