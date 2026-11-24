import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mockRouter } from "../../test/nextNavigation";
import { renderWithProviders } from "../../test/renderWithProviders";
import { sessionRestoreSettled } from "../../stores/authSlice";

const { post, get } = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }));
vi.mock("../../api", () => ({ default: { post, get } }));

const LoginPage = (await import("../LoginPage")).default;
const SignupPage = (await import("../SignupPage")).default;
const VerifyTokenPage = (await import("../VerifyTokenPage")).default;

// setCredentials ignores tokens that aren't an unexpired JWT.
const ACCESS_TOKEN = `h.${btoa(JSON.stringify({ exp: 4_102_444_800 }))}.s`;
const SESSION = {
  token: ACCESS_TOKEN,
  email: "sam@example.test",
  usertype: "USER",
  tier: "FREE",
};

function httpError(status: number, apiMessage?: string) {
  return Object.assign(new Error(`HTTP ${status}`), {
    isAxiosError: true,
    response: { status, data: {} },
    apiMessage,
  });
}

beforeEach(() => {
  post.mockReset();
  get.mockReset().mockRejectedValue(httpError(404)); // no company membership
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
});

describe("LoginPage", () => {
  it("validates before calling the API", async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />);
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(
      await screen.findByText("Enter your email address")
    ).toBeInTheDocument();
    expect(screen.getByText("Enter your password")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it("sends a captcha field and signs in", async () => {
    post.mockResolvedValue({ data: SESSION });
    const user = userEvent.setup();
    const { store } = renderWithProviders(<LoginPage />);
    await user.type(screen.getByLabelText("Email"), "sam@example.test");
    await user.type(screen.getByLabelText("Password"), "Correct1!horse");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(mockRouter.replace).toHaveBeenCalledWith("/"));
    expect(post).toHaveBeenCalledWith("/auth/login", {
      email: "sam@example.test",
      password: "Correct1!horse",
      // No site key in tests, so the field is present but empty.
      captcha: "",
    });
    expect(store.getState().auth).toMatchObject({
      accessToken: ACCESS_TOKEN,
      email: "sam@example.test",
    });
  });

  it("shows one message for bad credentials", async () => {
    post.mockRejectedValue(httpError(401, "Bad credentials"));
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />);
    await user.type(screen.getByLabelText("Email"), "sam@example.test");
    await user.type(screen.getByLabelText("Password"), "wrongpassword");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Invalid email or password."
    );
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });

  it("explains device verification instead of signing in", async () => {
    post.mockResolvedValue({
      data: { status: "DEVICE_VERIFICATION_REQUIRED" },
    });
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />);
    await user.type(screen.getByLabelText("Email"), "sam@example.test");
    await user.type(screen.getByLabelText("Password"), "Correct1!horse");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(
      await screen.findByText(/emailed you a link to approve it/)
    ).toBeInTheDocument();
    expect(mockRouter.replace).not.toHaveBeenCalled();
  });

  it("carries ?next= to the sign-up link", async () => {
    window.history.replaceState({}, "", "/login?next=%2Forders");
    const user = userEvent.setup();
    renderWithProviders(<LoginPage />);
    await user.click(screen.getByRole("link", { name: "Create an account" }));
    expect(mockRouter.push).toHaveBeenCalledWith("/signup?next=%2Forders");
  });
});

describe("SignupPage", () => {
  async function fill(password: string, confirm = password) {
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Email"), "new@example.test");
    await user.type(screen.getByLabelText("Password"), password);
    await user.type(screen.getByLabelText("Confirm password"), confirm);
    return user;
  }

  it("lists unmet password rules and blocks weak passwords", async () => {
    renderWithProviders(<SignupPage />);
    const user = await fill("weakpass");
    expect(screen.getByText("An uppercase letter")).toHaveTextContent(
      "(not met)"
    );
    expect(screen.getByText("At least 8 characters")).toHaveTextContent(
      "(met)"
    );
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(
      await screen.findByText("Your password doesn't meet all the requirements")
    ).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it("requires accepted terms, then matching passwords", async () => {
    renderWithProviders(<SignupPage />);
    const user = await fill("Str0ng!pass", "Str0ng!pasz");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(
      await screen.findByText("Accept the terms and privacy policy to continue")
    ).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(
      await screen.findByText("Passwords don't match")
    ).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it("creates the account and shows the inbox step", async () => {
    post.mockResolvedValue({ data: { message: "Verification email sent" } });
    renderWithProviders(<SignupPage />);
    const user = await fill("Str0ng!pass");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      await screen.findByRole("heading", { name: "Check your inbox" })
    ).toBeInTheDocument();
    expect(screen.getByText("new@example.test")).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith("/auth/signup", {
      email: "new@example.test",
      password: "Str0ng!pass",
      captcha: "",
    });
  });

  it("shows the backend's reason when signup is rejected", async () => {
    post.mockRejectedValue(
      httpError(409, "An account with this email already exists.")
    );
    renderWithProviders(<SignupPage />);
    const user = await fill("Str0ng!pass");
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "An account with this email already exists."
    );
  });
});

describe("VerifyTokenPage", () => {
  it("verifies an email token exactly once, even under StrictMode", async () => {
    window.history.replaceState({}, "", "/verify-email?token=abc");
    post.mockResolvedValue({ data: { message: "ok" } });
    renderWithProviders(
      <StrictMode>
        <VerifyTokenPage kind="email" />
      </StrictMode>
    );
    expect(
      await screen.findByRole("heading", { name: "Email verified" })
    ).toBeInTheDocument();
    expect(post).toHaveBeenCalledTimes(1);
    expect(post).toHaveBeenCalledWith("/auth/verify", null, {
      params: { token: "abc" },
    });
  });

  it("explains an expired or used link", async () => {
    window.history.replaceState({}, "", "/verify-email?token=used");
    post.mockRejectedValue(
      httpError(400, "This verification link has expired.")
    );
    renderWithProviders(<VerifyTokenPage kind="email" />);
    expect(
      await screen.findByRole("heading", {
        name: "We couldn't verify your email",
      })
    ).toBeInTheDocument();
    expect(
      screen.getByText("This verification link has expired.")
    ).toBeInTheDocument();
  });

  it("handles a link with no token without calling the API", async () => {
    window.history.replaceState({}, "", "/verify-email");
    renderWithProviders(<VerifyTokenPage kind="email" />);
    expect(
      await screen.findByText(/missing its verification code/)
    ).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it("waits for the page-load session restore before consuming a device link", async () => {
    window.history.replaceState({}, "", "/verify-device?token=dev");
    post.mockResolvedValue({ data: SESSION });
    const { store } = renderWithProviders(<VerifyTokenPage kind="device" />, {
      auth: { sessionRestored: false },
    });
    // A late refresh failure would otherwise wipe the session this creates.
    expect(post).not.toHaveBeenCalled();
    act(() => {
      store.dispatch(sessionRestoreSettled());
    });
    await waitFor(() => expect(mockRouter.replace).toHaveBeenCalledWith("/"));
    expect(post).toHaveBeenCalledTimes(1);
    expect(store.getState().auth.accessToken).toBe(ACCESS_TOKEN);
  });

  it("signs the device in after approval", async () => {
    window.history.replaceState({}, "", "/verify-device?token=dev");
    post.mockResolvedValue({ data: SESSION });
    const { store } = renderWithProviders(<VerifyTokenPage kind="device" />);
    await waitFor(() => expect(mockRouter.replace).toHaveBeenCalledWith("/"));
    expect(post).toHaveBeenCalledWith("/auth/verify-device", null, {
      params: { token: "dev" },
    });
    expect(store.getState().auth.accessToken).toBe(ACCESS_TOKEN);
  });
});
