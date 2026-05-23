import axios, { AxiosError, AxiosResponse } from "axios";
import { store } from "./stores";
import { setCredentials, clearCredentials } from "./stores/authSlice";
import Environment from "./configuration/Environment";
import type { ApiEnvelope, ApiError, ApiMeta } from "./types/api";

const api = axios.create({
  baseURL: Environment.backend_url,
  withCredentials: true, // send cookies for refresh
});

// Single shared promise for the in-flight token refresh. All concurrent 401 responses
// queue onto this promise rather than each triggering its own refresh request.
let refreshPromise: Promise<string> | null = null;

api.interceptors.request.use((config) => {
  const state = store.getState();
  const token = state.auth.accessToken;

  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Unwraps the standard ApiResponse envelope so call sites can keep using `resp.data`
// as the payload. Pagination metadata is exposed on `resp.meta`.
function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    "data" in value &&
    "error" in value
  );
}

function unwrapEnvelope(response: AxiosResponse): AxiosResponse {
  if (isEnvelope(response.data)) {
    const env = response.data;
    response.data = env.data;
    (response as AxiosResponse & { meta: ApiMeta | null }).meta = env.meta;
    (response as AxiosResponse & { apiMessage: string }).apiMessage = env.message;
  }
  return response;
}

api.interceptors.response.use(
  (response) => unwrapEnvelope(response),
  async (error: AxiosError) => {
    // Surface the typed envelope error so callers can read err.apiError.code etc.
    const env = error.response?.data;
    if (isEnvelope(env)) {
      (error as AxiosError & { apiError: ApiError | null }).apiError = env.error;
      (error as AxiosError & { apiMessage: string }).apiMessage = env.message;
    }

    const originalRequest = error.config as
      | (typeof error.config & { _retry?: boolean })
      | undefined;
    if (!originalRequest) return Promise.reject(error);

    const status = error.response?.status;
    if ((status === 401 || status === 403) && !originalRequest._retry) {
      originalRequest._retry = true;

      if (!refreshPromise) {
        refreshPromise = axios
          .post(
            `${Environment.backend_url}/auth/refresh`,
            {},
            { withCredentials: true }
          )
          .then((resp) => {
            // Refresh goes through raw axios (no interceptors), so unwrap by hand.
            const payload = isEnvelope(resp.data) ? resp.data.data : resp.data;
            const newToken: string = payload?.accessToken;
            store.dispatch(
              setCredentials({
                accessToken: newToken,
                email: payload?.email ?? null,
                role: payload?.role ?? null,
                tier: payload?.tier ?? null,
              })
            );
            return newToken;
          })
          .catch((err) => {
            store.dispatch(clearCredentials());
            return Promise.reject(err);
          })
          .finally(() => {
            refreshPromise = null;
          });
      }

      return refreshPromise.then((newToken) => {
        if (originalRequest.headers) {
          originalRequest.headers["Authorization"] = "Bearer " + newToken;
        }
        return api(originalRequest);
      });
    }

    return Promise.reject(error);
  }
);

export default api;
