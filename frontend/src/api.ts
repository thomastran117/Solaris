import axios from "axios";
import { store } from "./stores";
import { setCredentials, clearCredentials } from "./stores/authSlice";

const api = axios.create({
  baseURL: "http://localhost:8090/api",
  withCredentials: true, // send cookies for refresh
});

let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });

  failedQueue = [];
};

api.interceptors.request.use((config) => {
  const state = store.getState();
  const token = state.auth.accessToken;

  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    const status = error.response?.status;
    if ((status === 401 || status === 403) && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise(function (resolve, reject) {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (originalRequest.headers && token) {
              originalRequest.headers["Authorization"] = "Bearer " + token;
            }
            return api(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const resp = await axios.post(
          "http://localhost:8090/api/auth/refresh",
          {},
          { withCredentials: true }
        );

        const newToken = resp.data.accessToken;

        store.dispatch(
          setCredentials({
            accessToken: newToken,
            email: resp.data.email ?? null,
            role: resp.data.role ?? null,
          })
        );

        processQueue(null, newToken);

        if (originalRequest.headers) {
          originalRequest.headers["Authorization"] = "Bearer " + newToken;
        }
        return api(originalRequest);
      } catch (err) {
        processQueue(err, null);
        store.dispatch(clearCredentials());
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
