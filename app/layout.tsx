import type { Metadata } from "next";
import { headers } from "next/headers";
import "@fontsource-variable/manrope";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const requestHeaders = await headers();
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host");
  const protocol = requestHeaders.get("x-forwarded-proto") ?? "https";
  const baseUrl = host
    ? `${protocol}://${host}`
    : process.env.NEXT_PUBLIC_SITE_URL ?? "https://mysend.app";
  const socialImage = new URL("/og.png", baseUrl).toString();

  return {
    metadataBase: new URL(baseUrl),
    title: {
      default: "MySend: Send what you need.",
      template: "%s · MySend",
    },
    description:
      "Open a short-lived ShareRoom for text and files, then invite anyone with one memorable five-character code.",
    openGraph: {
      title: "MySend: Send what you need.",
      description:
        "One memorable code. A private, short-lived room for text and files.",
      type: "website",
      images: [{ url: socialImage, width: 1734, height: 907 }],
    },
    twitter: {
      card: "summary_large_image",
      title: "MySend: Send what you need.",
      description:
        "One memorable code. A private, short-lived room for text and files.",
      images: [socialImage],
    },
  };
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
