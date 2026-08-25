"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Account, api } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

const billingEnabled = process.env.NEXT_PUBLIC_BILLING_ENABLED === "true";

export function SettingsExperience() {
  const [account, setAccount] = useState<Account | null>(null);
  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Account>("/api/auth/me")
      .then(setAccount)
      .catch(() => setAccount(null))
      .finally(() => setChecking(false));
  }, []);

  async function upgrade() {
    setBusy(true);
    setError("");
    try {
      const checkout = await api<{ url: string }>("/api/billing/checkout", {
        method: "POST",
      });
      window.location.assign(checkout.url);
    } catch (caught) {
      setError(messageOf(caught));
      setBusy(false);
    }
  }

  async function manageBilling() {
    setBusy(true);
    setError("");
    try {
      const portal = await api<{ url: string }>("/api/billing/portal", {
        method: "POST",
      });
      window.location.assign(portal.url);
    } catch (caught) {
      setError(messageOf(caught));
      setBusy(false);
    }
  }

  if (checking) {
    return (
      <main className="settings-page">
        <SiteHeader compact />
        <section className="product-loading" role="status">
          <strong>Opening account settings</strong>
          <span>Checking your membership and security details.</span>
        </section>
      </main>
    );
  }

  if (!account) {
    return (
      <main className="settings-page">
        <SiteHeader compact />
        <section className="product-gate">
          <p className="section-kicker">Account settings</p>
          <h1>Log in to manage your account.</h1>
          <p>Email, membership, and security controls belong to the account that created them.</p>
          <div>
            <Link className="solid-link" href="/login">Log in</Link>
            <Link href="/">Continue as guest</Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="settings-page settings-page--account">
      <SiteHeader compact />
      <section className="account-settings-heading">
        <p className="section-kicker">Account settings</p>
        <h1>Your account, clearly.</h1>
        <p>Email, membership, and security. Room activity now lives in Dashboard.</p>
      </section>

      {error && (
        <div className="product-alert" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => setError("")}>Dismiss</button>
        </div>
      )}

      <section className="account-settings-grid">
        <article className="settings-panel settings-panel--identity">
          <header>
            <span className="settings-panel-index">01</span>
            <div>
              <p className="section-kicker">Profile</p>
              <h2>Account details</h2>
            </div>
          </header>
          <dl className="account-detail-list">
            <div>
              <dt>Email</dt>
              <dd>{account.email}</dd>
            </div>
            <div>
              <dt>Membership status</dt>
              <dd>{account.plan === "PREMIUM" ? "Premium" : "Free"}</dd>
            </div>
          </dl>
          <Link className="panel-link" href="/dashboard">Open Dashboard <span>→</span></Link>
        </article>

        <article className="settings-panel settings-panel--security" id="security">
          <header>
            <span className="settings-panel-index">02</span>
            <div>
              <p className="section-kicker">Security</p>
              <h2>Password</h2>
            </div>
          </header>
          <p>
            Password changes require a six-digit code sent to your account email.
          </p>
          <button className="panel-button" type="button" disabled>
            Change password
            <span>Security setup in progress</span>
          </button>
        </article>

        <article className="settings-panel settings-panel--membership">
          <header>
            <span className="settings-panel-index">03</span>
            <div>
              <p className="section-kicker">Membership</p>
              <h2>{account.plan === "PREMIUM" ? "Premium plan" : "Free plan"}</h2>
            </div>
          </header>
          <dl className="membership-limits">
            <div><dt>Active rooms</dt><dd>{account.activeRoomLimit}</dd></div>
            <div><dt>Room lifetime</dt><dd>{account.roomMinutes} min</dd></div>
            <div><dt>Clipboard</dt><dd>{account.clipboardCharacters.toLocaleString()}</dd></div>
            <div><dt>Room files</dt><dd>{formatBytes(account.roomFileBytes)}</dd></div>
          </dl>

          {!billingEnabled ? (
            <div className="membership-update" role="status">
              <strong>Premium is being updated.</strong>
              <p>Checkout and billing management will reopen soon.</p>
            </div>
          ) : account.plan === "PREMIUM" ? (
            <button className="panel-button" type="button" disabled={busy} onClick={() => void manageBilling()}>
              Manage billing <span>→</span>
            </button>
          ) : (
            <button className="panel-button" type="button" disabled={busy} onClick={() => void upgrade()}>
              Upgrade to Premium <span>→</span>
            </button>
          )}
        </article>
      </section>
    </main>
  );
}

function formatBytes(bytes: number) {
  return `${(bytes / 1_073_741_824).toFixed(0)} GB`;
}

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : "Something went wrong.";
}
