import { Navigate, Outlet } from "react-router-dom";
import { useSelector } from "react-redux";
import type { RootState } from "../../stores";

export default function ProtectedRoute() {
  const accessToken = useSelector((s: RootState) => s.auth.accessToken);
  if (!accessToken) return <Navigate to="/login" replace />;
  return <Outlet />;
}
