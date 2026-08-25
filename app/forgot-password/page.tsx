import type { Metadata } from "next";
import { PasswordResetExperience } from "../ui/PasswordResetExperience";

export const metadata: Metadata = {
  title: "Reset password",
  description: "Reset your MySend password with a code sent to your email.",
};

export default function ForgotPasswordPage() {
  return <PasswordResetExperience />;
}
