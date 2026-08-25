import type { Metadata } from "next";
import { AuthExperience } from "../ui/AuthExperience";

export const metadata: Metadata = {
  title: "Create account",
  description: "Create a free MySend account with one email verification step.",
};

export default function SignupPage() {
  return <AuthExperience mode="signup" />;
}
