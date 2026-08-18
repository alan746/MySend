"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { Account, api, Room } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

type AuthMode = "login" | "register";
type RegisterStep = "details" | "code";

type VerificationResult = {
  expiresAt: string;
  delivered: boolean;
  developmentCode?: string | null;
};

export function SettingsExperience() {
  const [account, setAccount] = useState<Account | null>(null);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [mode, setMode] = useState<AuthMode>("login");
  const [step, setStep] = useState<RegisterStep>("details");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [developmentCode, setDevelopmentCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

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

  async function submitLogin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const current = await api<Account>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      setAccount(current);
      setPassword("");
      setNotice("Signed in. Your membership limits now apply to new rooms.");
      setRooms(await api<Room[]>("/api/rooms"));
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  async function requestCode(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api<VerificationResult>("/api/auth/register/code", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      setDevelopmentCode(result.developmentCode || "");
      setStep("code");
      setNotice(
        result.delivered
          ? "Check your email. The code expires in 10 minutes."
          : "Development delivery is active; use the code shown below.",
      );
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  async function verifyCode(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const current = await api<Account>("/api/auth/register/verify", {
        method: "POST",
        body: JSON.stringify({ email, code }),
      });
      setAccount(current);
      setPassword("");
      setCode("");
      setDevelopmentCode("");
      setNotice("Your free account is ready.");
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    setBusy(true);
    setError("");
    try {
      await api<void>("/api/auth/logout", { method: "POST" });
      setAccount(null);
      setNotice("Signed out.");
      setMode("login");
      setStep("details");
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

  return (
    <main className="settings-page">
      <SiteHeader compact />
      <section className="settings-heading">
        <p className="section-kicker">Settings</p>
        <h1>Your rooms,<br /><em>your limits.</em></h1>
        <p>
          An account keeps your plan and active rooms together. Rooms still
          disappear on schedule.
        </p>
      </section>

      {notice && (
        <div className="settings-notice">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice("")}>×</button>
        </div>
      )}
      {error && (
        <div className="room-alert" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => setError("")}>Dismiss</button>
        </div>
      )}

      <section className="settings-grid">
        <article className="account-card">
          {checking ? (
            <div className="account-checking" role="status">Checking your account…</div>
          ) : account ? (
            <AccountSummary
              account={account}
              busy={busy}
              onLogout={() => void logout()}
            />
          ) : (
            <>
              <div className="auth-tabs" role="tablist" aria-label="Account action">
                <button
                  role="tab"
                  aria-selected={mode === "login"}
                  className={mode === "login" ? "active" : ""}
                  onClick={() => {
                    setMode("login");
                    setError("");
                  }}
                >
                  Sign in
                </button>
                <button
                  role="tab"
                  aria-selected={mode === "register"}
                  className={mode === "register" ? "active" : ""}
                  onClick={() => {
                    setMode("register");
                    setStep("details");
                    setError("");
                  }}
                >
                  Create account
                </button>
              </div>

              {mode === "login" ? (
                <form className="auth-form" onSubmit={submitLogin}>
                  <div className="account-intro">
                    <span>Welcome back</span>
                    <h2>Pick up where you left off.</h2>
                  </div>
                  <EmailField value={email} onChange={setEmail} />
                  <PasswordField value={password} onChange={setPassword} login />
                  <button className="primary-action" disabled={busy}>
                    {busy ? "Signing in…" : "Sign in"} <span>→</span>
                  </button>
                </form>
              ) : step === "details" ? (
                <form className="auth-form" onSubmit={requestCode}>
                  <div className="account-intro">
                    <span>Start free</span>
                    <h2>One email. One account.</h2>
                    <p>We verify your email once when you register.</p>
                  </div>
                  <EmailField value={email} onChange={setEmail} />
                  <PasswordField value={password} onChange={setPassword} />
                  <button className="primary-action" disabled={busy}>
                    {busy ? "Sending code…" : "Send verification code"} <span>→</span>
                  </button>
                </form>
              ) : (
                <form className="auth-form verify-form" onSubmit={verifyCode}>
                  <button
                    className="back-link"
                    type="button"
                    onClick={() => setStep("details")}
                  >
                    ← Change email
                  </button>
                  <div className="account-intro">
                    <span>Check your inbox</span>
                    <h2>Enter the six-digit code.</h2>
                    <p>Sent to {email}. It expires after 10 minutes.</p>
                  </div>
                  {developmentCode && (
                    <div className="development-code">
                      Local development code: <strong>{developmentCode}</strong>
                    </div>
                  )}
                  <label className="code-field verification-code">
                    <span>Verification code</span>
                    <input
                      value={code}
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      maxLength={6}
                      placeholder="000000"
                      onChange={(event) =>
                        setCode(event.target.value.replace(/\D/g, "").slice(0, 6))
                      }
                    />
                  </label>
                  <button className="primary-action" disabled={busy || code.length !== 6}>
                    {busy ? "Verifying…" : "Create free account"} <span>→</span>
                  </button>
                </form>
              )}
            </>
          )}
        </article>

        <article className="membership-card">
          <div className="membership-top">
            <span className="premium-tag">Premium</span>
            <div><strong>$9.99</strong><small>CAD / month</small></div>
          </div>
          <h2>More room for the work,<br />not permanent clutter.</h2>
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
          {account?.plan === "PREMIUM" ? (
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
                disabled={busy || !account}
                onClick={() => void upgrade()}
              >
                Upgrade to Premium <span>↗</span>
              </button>
              {account?.billingProfileAvailable && (
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
          {!account && <p className="membership-note">Create or sign in to upgrade.</p>}
        </article>
      </section>

      <section className="active-rooms" aria-labelledby="active-rooms-title">
        <div>
          <span className="section-kicker">My ShareRooms</span>
          <h2 id="active-rooms-title">Active now</h2>
        </div>
        {rooms.length === 0 ? (
          <div className="rooms-empty">
            <p>
              {account
                ? "No active rooms on this account."
                : "My ShareRooms is available after free registration. Guest rooms still work without login."}
            </p>
            <Link href="/">{account ? "Create a ShareRoom" : "Continue as guest"}</Link>
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

function EmailField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="text-field">
      <span>Email</span>
      <input
        type="email"
        value={value}
        required
        maxLength={320}
        autoComplete="email"
        placeholder="you@example.com"
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function PasswordField({
  value,
  onChange,
  login = false,
}: {
  value: string;
  onChange: (value: string) => void;
  login?: boolean;
}) {
  return (
    <label className="text-field">
      <span>Password {login ? null : <small>10+ characters</small>}</span>
      <input
        type="password"
        value={value}
        required
        minLength={login ? undefined : 10}
        maxLength={100}
        autoComplete={login ? "current-password" : "new-password"}
        placeholder={login ? "Your password" : "Choose a strong password"}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
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
