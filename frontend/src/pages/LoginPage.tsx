import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import { setCredentials } from "../stores/authSlice";
import type { AppDispatch, RootState } from "../stores";
import axios from "axios";
import "../styles/login.css";

const images = ["/carousel1.jpg", "/carousel2.jpg", "/carousel3.jpg"];

const api = axios.create({
  baseURL: "http://localhost:8090/api",
  withCredentials: true,
});

export default function LoginPage() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({ username: "", password: "" });
  const [slide, setSlide] = useState(0);
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const dispatch = useDispatch<AppDispatch>();
  const authState = useSelector((state: RootState) => state.auth);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await api.post("/auth/login", {
        email: formData.username,
        password: formData.password,
      });
      const { token, email, usertype } = res.data;
      dispatch(setCredentials({ accessToken: token, email, role: usertype }));

      navigate("/dashboard");
    } catch (err) {
      console.error("Login failed", err);
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleLogin = () => {
    const clientId =
      "199609700164-kob74jigm65p2i1gtcmc3dkk60766tk4.apps.googleusercontent.com";
    const redirectUri = "http://localhost:3090/auth/google";
    const scope = "openid email profile";

    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: "id_token",
      scope,
      nonce: crypto.randomUUID(),
      prompt: "select_account",
    });

    const url = `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;

    const width = 600;
    const height = 700;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;

    const popup = window.open(
      url,
      "GoogleLogin",
      `width=${width},height=${height},left=${left},top=${top},resizable,scrollbars`
    );

    const listener = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return;
      if (event.data.type === "google_oauth_token") {
        popup?.close();
        handleGoogleResponse(event.data.token);
        window.removeEventListener("message", listener);
      }
    };
    window.addEventListener("message", listener);
  };

  const handleGoogleResponse = async (idToken: string) => {
    setLoading(true);
    try {
      const res = await api.post("/auth/google", { idToken });
      const { token, email, usertype } = res.data;
      dispatch(setCredentials({ accessToken: token, email, role: usertype }));
      navigate("/dashboard");
    } catch (err) {
      console.error("Google login failed", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const interval = setInterval(() => {
      setSlide((prev) => (prev + 1) % images.length);
    }, 4000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (authState.accessToken) {
      console.log("✅ Redux updated:", authState);
      navigate("/dashboard");
    }
  }, [authState, navigate]);
  return (
    <div className="min-h-screen bg-gradient-to-br from-[#0a0f1c] via-[#111827] to-[#0d1a2f] flex items-center justify-center px-4 py-10">
      <div
        className="w-full max-w-4xl min-h-[600px] md:h-[700px] 
          bg-[#0D1B2A]/95 backdrop-blur-xl 
          rounded-3xl shadow-[0_0_25px_rgba(0,115,255,0.3)] 
          border border-[#1E3A5F]/70 
          flex flex-col md:flex-row"
      >
        {/* Left carousel */}
        <div className="md:w-1/2 flex flex-col justify-between p-4">
          <div className="relative w-full h-full overflow-hidden bg-black rounded-2xl">
            <img
              key={slide}
              src={images[slide]}
              alt={`Slide ${slide}`}
              className="object-cover w-full h-full rounded-2xl fade-in-zoom"
            />
            <div className="absolute top-0 left-0 w-full p-4 flex justify-between items-center z-10">
              <span className="text-xl font-bold text-white">SW</span>
              <button className="text-sm border border-gray-500 px-3 py-1 rounded-md hover:bg-gray-800 text-white">
                Back to Home
              </button>
            </div>

            <div className="absolute bottom-6 w-full text-center text-white z-10">
              <p className="font-semibold text-lg">
                Capturing Moments, Creating Memories
              </p>
              <div className="mt-4 flex justify-center space-x-2">
                {images.map((_, idx) => (
                  <button
                    key={idx}
                    onClick={() => setSlide(idx)}
                    className={`h-2 rounded-full transition-all duration-300 ${
                      slide === idx ? "w-8 bg-blue-400" : "w-2 bg-gray-500"
                    }`}
                    aria-label={`Go to slide ${idx + 1}`}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Right auth form */}
        <div className="md:w-1/2 flex-1 p-8 flex flex-col justify-center">
          <div className="w-full">
            <h2 className="text-3xl font-bold mt-4 mb-2 text-center text-blue-400">
              Login to your account
            </h2>
            <p className="text-sm text-gray-400 mb-8 text-center">
              Don&apos;t have an account?{" "}
              <a href="/signup" className="text-blue-400 hover:underline">
                Sign up
              </a>
            </p>

            <form onSubmit={handleSubmit} className="space-y-4">
              {/* Username input */}
              <div className="flex items-center border border-gray-600 rounded-md bg-[#1e293b] px-3 py-2 focus-within:border-blue-500 transition">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  className="h-5 w-5 text-gray-400 mr-3"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M5.121 17.804A9.97 9.97 0 0112 15c2.21 0 4.24.722 5.879 1.939M15 10a3 3 0 11-6 0 3 3 0 016 0z"
                  />
                </svg>
                <input
                  type="text"
                  name="username"
                  placeholder="Email"
                  value={formData.username}
                  onChange={handleChange}
                  className="bg-transparent w-full focus:outline-none text-white placeholder-gray-400"
                  required
                />
              </div>

              {/* Password input */}
              <div className="flex items-center border border-gray-600 rounded-md bg-[#1e293b] px-3 py-2">
                <svg
                  className="h-5 w-5 text-gray-400 mr-3"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 11c-1.1 0-2 .9-2 2v2h4v-2c0-1.1-.9-2-2-2z"
                  />
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 9V7a6 6 0 1112 0v2M5 11h14v10H5z"
                  />
                </svg>
                <input
                  type={showPassword ? "text" : "password"}
                  name="password"
                  placeholder="Password"
                  value={formData.password}
                  onChange={handleChange}
                  className="bg-transparent w-full focus:outline-none text-white"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="ml-2 p-1 rounded-md transition duration-200 hover:bg-gray-700/50 focus:outline-none"
                >
                  {showPassword ? (
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      className="h-5 w-5 text-gray-400 hover:text-white transition"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M13.875 18.825A10.05 10.05 0 0112 19c-5.523 0-10-4.477-10-10a9.96 9.96 0 012.291-6.405m2.62-2.62A9.96 9.96 0 0112 1c5.523 0 10 4.477 10 10 0 2.071-.632 3.994-1.715 5.585m-3.082 3.082A9.96 9.96 0 0112 21c-1.05 0-2.065-.162-3.018-.463M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                      />
                    </svg>
                  ) : (
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      className="h-5 w-5 text-gray-400 hover:text-white transition"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                      />
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
                      />
                    </svg>
                  )}
                </button>
              </div>

              {/* Submit */}
              <button
                type="submit"
                disabled={loading}
                className={`w-full flex justify-center items-center gap-2 ${
                  loading
                    ? "bg-blue-500 cursor-not-allowed"
                    : "bg-blue-600 hover:bg-blue-700 hover:scale-[1.02] hover:shadow-[0_0_15px_rgba(0,115,255,0.6)]"
                } transition text-white font-semibold py-2 rounded-md shadow-md transform duration-200`}
              >
                {loading && (
                  <svg
                    className="animate-spin h-5 w-5 text-white"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    ></circle>
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8v8H4z"
                    ></path>
                  </svg>
                )}
                {loading ? "Processing..." : "Login"}
              </button>
            </form>

            {/* OAuth divider */}
            <div className="mt-8">
              <div className="flex items-center text-gray-500 mb-4">
                <hr className="flex-grow border-gray-700" />
                <span className="mx-4 text-sm">or continue with</span>
                <hr className="flex-grow border-gray-700" />
              </div>

              {/* OAuth buttons */}
              <div className="flex flex-wrap justify-center gap-4">
                <button
                  onClick={handleGoogleLogin}
                  className="w-1/2 max-w-[180px] py-2 px-4 border border-gray-600 rounded-md flex items-center justify-center gap-2 
                    bg-[#1e293b] hover:bg-[#243447] transition duration-200 shadow-md"
                >
                  {/* Google Icon */}
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 533.5 544.3"
                    className="w-5 h-5"
                  >
                    <path
                      fill="#4285F4"
                      d="M533.5 278.4c0-17.4-1.5-34.1-4.5-50.3H272v95.2h146.9c-6.3 34-25 62.9-53.6 82.2l86.6 67.2c50.6-46.6 81.6-115.3 81.6-194.3z"
                    />
                    <path
                      fill="#34A853"
                      d="M272 544.3c72.9 0 134.1-24.1 178.8-65.7l-86.6-67.2c-24 16.1-54.7 25.7-92.2 25.7-70.9 0-130.9-47.8-152.4-111.9l-89.4 69c41.8 82.7 128.1 150.1 242.8 150.1z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M119.6 325.2c-10.9-32.6-10.9-67.7 0-100.3l-89.4-69C10.8 201.1 0 238.5 0 278s10.8 76.9 30.2 122.1l89.4-69z"
                    />
                    <path
                      fill="#EA4335"
                      d="M272 107.7c39.7 0 75.5 13.7 103.6 40.5l77.3-77.3C406.1 24.1 344.9 0 272 0 157.3 0 71 67.4 30.2 155.9l89.4 69c21.5-64.1 81.5-111.9 152.4-111.9z"
                    />
                  </svg>
                  <span className="text-gray-200 font-medium">Google</span>
                </button>

                <button
                  className="w-1/2 max-w-[180px] py-2 px-4 border border-gray-600 rounded-md flex items-center justify-center gap-2 
                    bg-[#1e293b] hover:bg-[#243447] transition duration-200 shadow-md"
                >
                  {/* Microsoft Icon */}
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 23 23"
                    className="w-5 h-5"
                  >
                    <rect x="1" y="1" width="9" height="9" fill="#F25022" />
                    <rect x="13" y="1" width="9" height="9" fill="#7FBA00" />
                    <rect x="1" y="13" width="9" height="9" fill="#00A4EF" />
                    <rect x="13" y="13" width="9" height="9" fill="#FFB900" />
                  </svg>
                  <span className="text-gray-200 font-medium">Microsoft</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
