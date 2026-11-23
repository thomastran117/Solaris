"use client";

import { useEffect, useState } from "react";
import { CheckCircle, XCircle } from "lucide-react";

export default function AuthCallback() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const hash = new URLSearchParams(window.location.hash.substring(1));
      const idToken = hash.get("id_token");

      if (idToken && window.opener) {
        window.opener.postMessage(
          { type: "google_oauth_token", token: idToken },
          window.location.origin
        );
        window.close();
      } else {
        setError("Missing Google ID token. Please try again.");
      }
    } catch {
      setError("Something went wrong. Please try again.");
    }
  }, []);

  const handleRetry = () => {
    window.location.href = "/login";
  };

  return (
    <div className="h-screen flex items-center justify-center bg-gradient-to-b from-black via-[#0a1a33] to-[#020617]">
      <div className="bg-[#0f172a] shadow-2xl rounded-2xl p-8 w-[380px] text-center border border-blue-700/40">
        <div className="flex justify-center mb-4">
          {!error ? (
            <div className="relative">
              <div className="w-20 h-20 rounded-full bg-gradient-to-tr from-blue-500 to-blue-300 flex items-center justify-center animate-bounce shadow-lg shadow-blue-500/30">
                <span className="text-4xl">🤖</span>
              </div>
              <CheckCircle
                className="absolute -bottom-2 -right-2 text-green-400 bg-black rounded-full"
                size={24}
              />
            </div>
          ) : (
            <div className="relative">
              <div className="w-20 h-20 rounded-full bg-gradient-to-tr from-red-600 to-red-400 flex items-center justify-center animate-pulse shadow-lg shadow-red-600/30">
                <span className="text-4xl">🤖</span>
              </div>
              <XCircle
                className="absolute -bottom-2 -right-2 text-red-400 bg-black rounded-full"
                size={24}
              />
            </div>
          )}
        </div>

        {!error ? (
          <>
            <h2 className="text-xl font-semibold text-white mb-2">
              Finishing login…
            </h2>
            <p className="text-blue-200 text-sm">
              Please wait while we complete your Google sign-in.
            </p>
            <div className="w-12 h-12 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mt-6"></div>
          </>
        ) : (
          <>
            <h2 className="text-xl font-semibold text-red-400 mb-2">
              Login Failed
            </h2>
            <p className="text-blue-200 text-sm mb-4">{error}</p>
            <button
              onClick={handleRetry}
              className="px-4 py-2 bg-gradient-to-r from-blue-500 to-blue-600 hover:from-blue-600 hover:to-blue-700 text-white font-medium rounded-lg transition-colors"
            >
              Retry Login
            </button>
          </>
        )}
      </div>
    </div>
  );
}
