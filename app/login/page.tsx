import type { Metadata } from "next";
import { AuthExperience } from "../ui/AuthExperience";

export const metadata: Metadata = {
  title: "Log in",
  description: "Log in to see your active MySend ShareRooms and account limits.",
};

export default function LoginPage() {
  return <AuthExperience mode="login" />;
}
