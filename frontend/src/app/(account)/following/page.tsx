import type { Metadata } from "next";
import FollowingPage from "@views/FollowingPage";

export const metadata: Metadata = { title: "Following" };

export default function Page() {
  return <FollowingPage />;
}
