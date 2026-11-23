import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import axios from "axios";
import { makeStore } from "../stores";
import { setCredentials } from "../stores/authSlice";
import { bindApiStore } from "./authBridge";
import { refreshSession } from "./session";

vi.mock("axios");
const mockPost = vi.mocked(axios.post);

// A structurally valid JWT whose exp is far in the future (setCredentials drops expired tokens).
const token = `h.${btoa(JSON.stringify({ exp: 4_102_444_800 }))}.s`;

function envelope(data: unknown) {
  return {
    data: { success: true, message: "ok", data, error: null, meta: null },
  };
}

describe("refreshSession", () => {
  let store: ReturnType<typeof makeStore>;

  beforeEach(() => {
    store = makeStore();
    bindApiStore(store);
  });
  afterEach(() => {
    mockPost.mockReset();
  });

  it("shares one in-flight refresh between concurrent callers (refresh tokens are single-use)", async () => {
    let resolve!: (v: unknown) => void;
    mockPost.mockReturnValue(new Promise((r) => (resolve = r)) as never);

    const a = refreshSession();
    const b = refreshSession();
    expect(mockPost).toHaveBeenCalledTimes(1);

    resolve(
      envelope({
        accessToken: token,
        email: "a@b.c",
        role: "USER",
        tier: "FREE",
      })
    );
    await expect(a).resolves.toBe(token);
    await expect(b).resolves.toBe(token);
    expect(store.getState().auth).toMatchObject({
      accessToken: token,
      email: "a@b.c",
      role: "USER",
    });
  });

  it("starts a new request once the previous one settled", async () => {
    mockPost.mockResolvedValue(envelope({ accessToken: token }) as never);
    await refreshSession();
    await refreshSession();
    expect(mockPost).toHaveBeenCalledTimes(2);
  });

  it("clears credentials and rejects when the cookie is missing or expired", async () => {
    store.dispatch(setCredentials({ accessToken: token, email: "a@b.c" }));
    mockPost.mockRejectedValue(new Error("401") as never);
    await expect(refreshSession()).rejects.toThrow("401");
    expect(store.getState().auth.accessToken).toBeNull();
    expect(store.getState().auth.email).toBeNull();
  });

  it("rejects a success response that carries no token", async () => {
    mockPost.mockResolvedValue(envelope({}) as never);
    await expect(refreshSession()).rejects.toThrow();
    expect(store.getState().auth.accessToken).toBeNull();
  });
});
