import axios, { AxiosError, type AxiosResponse } from "axios";
import Environment from "./configuration/Environment";
import type { ApiError, ApiMeta } from "./types/api";
import { getAccessToken } from "./api/authBridge";
import { isEnvelope } from "./api/envelope";
import { refreshSession } from "./api/session";

const api = axios.create({
  baseURL: Environment.backend_url,
  withCredentials: true, // send cookies for refresh
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();

  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Unwraps the standard ApiResponse envelope so call sites can keep using `resp.data`
// as the payload. Pagination metadata is exposed on `resp.meta`.
function unwrapEnvelope(response: AxiosResponse): AxiosResponse {
  if (isEnvelope(response.data)) {
    const env = response.data;
    response.data = env.data;
    (response as AxiosResponse & { meta: ApiMeta | null }).meta = env.meta;
    (response as AxiosResponse & { apiMessage: string }).apiMessage =
      env.message;
  }
  return response;
}

api.interceptors.response.use(
  (response) => unwrapEnvelope(response),
  async (error: AxiosError) => {
    // Surface the typed envelope error so callers can read err.apiError.code etc.
    const env = error.response?.data;
    if (isEnvelope(env)) {
      (error as AxiosError & { apiError: ApiError | null }).apiError =
        env.error;
      (error as AxiosError & { apiMessage: string }).apiMessage = env.message;
    }

    const originalRequest = error.config as
      | (typeof error.config & { _retry?: boolean })
      | undefined;
    if (!originalRequest) return Promise.reject(error);

    const status = error.response?.status;
    if ((status === 401 || status === 403) && !originalRequest._retry) {
      originalRequest._retry = true;

      // All concurrent 401s share one refresh (refresh tokens are single-use).
      return refreshSession().then((newToken) => {
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
