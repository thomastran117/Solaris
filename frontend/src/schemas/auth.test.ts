import { describe, expect, it } from "vitest";
import { PASSWORD_RULES, loginSchema, signupSchema } from "./auth";

const meetsRules = (password: string) =>
  PASSWORD_RULES.every((rule) => rule.test(password));

/**
 * Mirrors backend `StrongPasswordValidator` (min length 8, upper, lower, digit,
 * and one of @$!%*?&._-). Keep these cases in step with the Java tests so the
 * form never accepts a password the API rejects, or the reverse.
 */
describe.each([
  ["Str0ng!pass", true],
  ["Aa1@aaaa", true],
  ["Aa1.aaaa", true],
  ["Aa1_aaaa", true],
  ["Aa1-aaaa", true],
  ["Aa1@aaa", false], // 7 characters
  ["aa1@aaaa", false], // no uppercase
  ["AA1@AAAA", false], // no lowercase
  ["Aaa@aaaa", false], // no digit
  ["Aa1aaaaa", false], // no symbol
  ["Aa1#aaaa", false], // # is not an accepted symbol
])("password %s", (password, valid) => {
  it(`is ${valid ? "accepted" : "rejected"}`, () => {
    expect(meetsRules(password)).toBe(valid);
  });
});

describe("signupSchema", () => {
  const base = {
    email: "new@example.test",
    password: "Str0ng!pass",
    confirmPassword: "Str0ng!pass",
    acceptTerms: true,
  };

  it("accepts a complete form and trims the email", () => {
    const parsed = signupSchema.parse({
      ...base,
      email: "  new@example.test ",
    });
    expect(parsed.email).toBe("new@example.test");
  });

  it("rejects mismatched passwords on the confirm field", () => {
    const result = signupSchema.safeParse({ ...base, confirmPassword: "nope" });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0].path).toEqual(["confirmPassword"]);
  });

  it("requires the terms checkbox", () => {
    expect(
      signupSchema.safeParse({ ...base, acceptTerms: false }).success
    ).toBe(false);
  });
});

describe("loginSchema", () => {
  it("matches LoginRequest's minimum password length", () => {
    expect(
      loginSchema.safeParse({ email: "a@b.co", password: "1234567" }).success
    ).toBe(false);
    expect(
      loginSchema.safeParse({ email: "a@b.co", password: "12345678" }).success
    ).toBe(true);
  });
});
