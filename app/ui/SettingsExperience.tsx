"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Account, api, Room } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

const billingEnabled = process.env.NEXT_PUBLIC_BILLING_ENABLED === "true";

export function SettingsExperience() {
  const [account, setAccount] = useState<Account | null>(null);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Account>("/api/auth/me")
      .then(async (current) => {
        setAccount(current);
        setRooms(await api<Room[]>("/api/rooms"));
      })
      .catch(() => {
        setAccount(null);
        setRooms([]);
      })
      .finally(() => setChecking(false));
  }, []);

  async function logout() {
    setBusy(true);
    setError("");
    try {
      await api<void>("/api/auth/logout", { method: "POST" });
      setAccount(null);
      setRooms([]);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

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
        <section className="settings-loading" role="status">
          <span>Account settings</span>
          <p>Checking your account...</p>
        </section>
      </main>
    );
  }

  if (!account) {
    return (
      <main className="settings-page">
        <SiteHeader compact />
        <section className="settings-gate" aria-labelledby="settings-gate-title">
          <div>
            <p className="section-kicker">Account settings</p>
            <h1 id="settings-gate-title">Log in to continue.</h1>
            <p>
              Settings keeps your active rooms and account limits together.
              Guest sharing stays available without an account.
            </p>
          </div>
          <div className="settings-gate-actions">
            <Link className="settings-gate-primary" href="/login">Log in</Link>
            <Link href="/signup">Create an account</Link>
            <Link href="/">Continue as guest</Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="settings-page">
      <SiteHeader compact />
      <section className="settings-heading">
        <p className="section-kicker">Account settings</p>
        <h1>Rooms and limits.</h1>
        <p>
          Your plan, active ShareRooms, and account controls in one place.
          Shared content still disappears on schedule.
        </p>
      </section>

      {error && (
        <div className="room-alert" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => setError("")}>Dismiss</button>
        </div>
      )}

      <section className="settings-grid">
        <article className="account-card">
          <AccountSummary
            account={account}
            busy={busy}
            onLogout={() => void logout()}
          />
        </article>

        <article className="membership-card">
          <div className="membership-top">
            <span className="premium-tag">Premium</span>
            <div><strong>$9.99</strong><small>CAD / month</small></div>
          </div>
          <h2>More room for the work, not permanent clutter.</h2>
          <div className="membership-comparison">
            <div><span>Active rooms</span><b>2</b><strong>5</strong></div>
            <div><span>Room lifetime</span><b>60m</b><strong>3h</strong></div>
            <div><span>Clipboard</span><b>10k</b><strong>100k</strong></div>
            <div><span>Room files</span><b>1 GB</b><strong>5 GB</strong></div>
          </div>
          <div className="comparison-key">
            <span><i className="free-dot" /> Free</span>
            <span><i className="premium-dot" /> Premium</span>
          </div>
          {!billingEnabled ? (
            <div className="premium-maintenance" role="status">
              <span>Plan update</span>
              <strong>Premium is being updated.</strong>
              <p>Checkout and billing management will reopen soon.</p>
            </div>
          ) : account.plan === "PREMIUM" ? (
            <>
              <div className="current-plan">Premium is active on this account.</div>
              <button
                className="premium-action"
                type="button"
                disabled={busy}
                onClick={() => void manageBilling()}
              >
                Manage billing <span>↗</span>
              </button>
            </>
          ) : (
            <>
              <button
                className="premium-action"
                type="button"
                disabled={busy}
                onClick={() => void upgrade()}
              >
                Upgrade to Premium <span>↗</span>
              </button>
              {account.billingProfileAvailable && (
                <button
                  className="membership-manage"
                  type="button"
                  disabled={busy}
                  onClick={() => void manageBilling()}
                >
                  View previous billing
                </button>
              )}
            </>
          )}
        </article>
      </section>

      <section className="active-rooms" aria-labelledby="active-rooms-title">
        <div>
          <span className="section-kicker">My ShareRooms</span>
          <h2 id="active-rooms-title">Active now</h2>
        </div>
        {rooms.length === 0 ? (
          <div className="rooms-empty">
            <p>No active rooms on this account.</p>
            <Link href="/">Create a ShareRoom</Link>
          </div>
        ) : (
          <div className="rooms-list">
            {rooms.map((room) => (
              <Link href={`/room/${room.accessCode}`} key={room.id}>
                <span>{room.accessCode}</span>
                <div>
                  <strong>{room.visibility.toLowerCase()} room</strong>
                  <small>{room.remainingEntries} entries left</small>
                </div>
                <time>{minutesLeft(room.expiresAt)}m left</time>
                <b>→</b>
              </Link>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

function AccountSummary({
  account,
  busy,
  onLogout,
}: {
  account: Account;
  busy: boolean;
  onLogout: () => void;
}) {
  return (
    <div className="account-summary">
      <div className="account-avatar">{account.email.slice(0, 1).toUpperCase()}</div>
      <span className="section-kicker">Signed in</span>
      <h2>{account.email}</h2>
      <span className={`plan-pill ${account.plan === "PREMIUM" ? "is-premium" : ""}`}>
        {account.plan.toLowerCase()} membership
      </span>
      <dl>
        <div><dt>Active rooms</dt><dd>{account.activeRoomLimit}</dd></div>
        <div><dt>Maximum time</dt><dd>{account.roomMinutes} min</dd></div>
        <div><dt>Clipboard</dt><dd>{account.clipboardCharacters.toLocaleString()}</dd></div>
        <div><dt>File storage</dt><dd>{formatBytes(account.roomFileBytes)}</dd></div>
      </dl>
      <button className="quiet-button" type="button" disabled={busy} onClick={onLogout}>
        Sign out
      </button>
    </div>
  );
}

function minutesLeft(value: string) {
  return Math.max(0, Math.ceil((new Date(value).getTime() - Date.now()) / 60_000));
}

function formatBytes(bytes: number) {
  return `${(bytes / 1_073_741_824).toFixed(0)} GB`;
}

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : "Something went wrong.";
}
