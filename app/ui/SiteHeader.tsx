"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Account, api } from "../lib/api";
import { BrandMark } from "./BrandMark";

type SiteHeaderProps = {
  compact?: boolean;
};

export function SiteHeader({ compact = false }: SiteHeaderProps) {
  const pathname = usePathname();
  const [account, setAccount] = useState<Account | null>(null);
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    let active = true;
    api<Account>("/api/auth/me")
      .then((current) => {
        if (active) setAccount(current);
      })
      .catch(() => {
        if (active) setAccount(null);
      });
    return () => {
      active = false;
    };
  }, []);

  async function logout() {
    setSigningOut(true);
    try {
      await api<void>("/api/auth/logout", { method: "POST" });
      window.location.replace("/");
    } catch {
      setSigningOut(false);
    }
  }

  return (
    <header className={`site-header ${compact ? "site-header--compact" : ""}`}>
      <Link className="brand" href="/" aria-label="MySend home">
        <BrandMark />
        <span>MySend</span>
      </Link>
      <nav aria-label="Main navigation">
        {account ? (
          <div className="nav-auth nav-auth--signed-in" aria-label="Account navigation">
            <Link
              aria-current={pathname === "/dashboard" ? "page" : undefined}
              href="/dashboard"
            >
              Dashboard
            </Link>
            <Link
              aria-current={pathname === "/settings" ? "page" : undefined}
              href="/settings"
            >
              Settings
            </Link>
            <button type="button" disabled={signingOut} onClick={() => void logout()}>
              {signingOut ? "Signing out" : "Log out"}
            </button>
          </div>
        ) : (
          <div className="nav-auth" aria-label="Account navigation">
            <Link className="nav-section" href="/#how-it-works">How it works</Link>
            <Link className="nav-section" href="/#plans">Plans</Link>
            <Link className="nav-login" href="/login">Log in</Link>
            <Link className="nav-account" href="/signup">Sign up</Link>
          </div>
        )}
      </nav>
    </header>
  );
}
