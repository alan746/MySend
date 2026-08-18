import type { Metadata } from "next";
import { RoomExperience } from "../../ui/RoomExperience";

export const metadata: Metadata = {
  title: "ShareRoom",
  description: "A short-lived MySend clipboard and file board.",
};

export default async function RoomPage({
  params,
}: {
  params: Promise<{ code: string }>;
}) {
  const { code } = await params;
  const normalizedCode = code.toUpperCase();
  return <RoomExperience key={normalizedCode} code={normalizedCode} />;
}
