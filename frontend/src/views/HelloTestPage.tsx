"use client";

import { useState } from "react";
import api from "../api";
import { useSelector } from "react-redux";
import type { RootState } from "../stores";

export default function HelloTestPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const auth = useSelector((state: RootState) => state.auth);

  const callHello = async () => {
    setMessage(null);
    setError(null);

    try {
      const resp = await api.get("/auth/hello");
      setMessage(resp.data);
    } catch (err: any) {
      console.error(err);

      let msg = "Request failed";
      if (err.response) {
        if (typeof err.response.data === "string") {
          msg = err.response.data;
        } else if (err.response.data?.error) {
          msg = err.response.data.error;
        } else {
          msg = JSON.stringify(err.response.data); // fallback
        }

        if (err.response.status === 401) {
          msg = "⚠️ Unauthorized: token missing, expired, or invalid.";
        } else if (err.response.status === 403) {
          msg =
            "🚫 Forbidden: you don’t have permission to access this resource.";
        }
      } else if (err.message) {
        msg = err.message;
      }

      setError(msg);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center h-screen text-white bg-gray-900">
      <h1 className="text-2xl font-bold mb-4">Test /auth/hello</h1>

      <div className="mb-4">
        <p>
          <strong>Current Token:</strong>{" "}
          {auth.accessToken ? (
            <span className="text-green-400">{auth.accessToken}</span>
          ) : (
            <span className="text-red-400">No token</span>
          )}
        </p>
        <p>
          <strong>Email:</strong> {auth.email || "N/A"}
        </p>
        <p>
          <strong>Role:</strong> {auth.role || "N/A"}
        </p>
      </div>

      <button
        onClick={callHello}
        className="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg shadow-md"
      >
        Call /auth/hello
      </button>

      {message && (
        <p className="mt-4 p-2 bg-green-800 rounded-lg">✅ {message}</p>
      )}
      {error && <p className="mt-4 p-2 bg-red-800 rounded-lg">❌ {error}</p>}
    </div>
  );
}
