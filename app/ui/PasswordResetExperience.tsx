"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { Account, api } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

type Step = "email" | "code";

type PasswordCodeResult = {
  expiresAt: string;
  developmentCode?: string | null;
};

export function PasswordResetExperience() {
  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [developmentCode, setDevelopmentCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function requestCode(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api<PasswordCodeResult>("/api/auth/password/code", {
        method: "POST",
        body: JSON.stringify({ email }),
      });
      setDevelopmentCode(result.developmentCode || "");
      setStep("code");
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  async function resetPassword(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api<Account>("/api/auth/password/reset", {
        method: "POST",
        body: JSON.stringify({ email, code, newPassword: password }),
      });
      window.location.replace("/dashboard");
    } catch (caught) {
      setError(messageOf(caught));
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <SiteHeader />
      <section className="auth-layout" aria-labelledby="password-reset-title">
        <div className="auth-story">
          <p className="auth-context">Account recovery</p>
          <h1 id="password-reset-title">A short code. A fresh password.</h1>
          <p className="auth-story-copy">
            We send one six-digit code to your account email. It expires after 10 minutes.
          </p>
          <div className="auth-promise">
            <p>Remembered your password?</p>
            <Link href="/login">Return to login</Link>
          </div>
        </div>

        <div className="auth-panel">
          <div className="auth-form-wrap">
            {step === "code" && (
              <button
                className="auth-back"
                type="button"
                onClick={() => {
                  setStep("email");
                  setCode("");
                  setPassword("");
                  setError("");
                }}
              >
                Use a different email
              </button>
            )}

            <header className="auth-form-heading">
              <h2>{step === "email" ? "Reset password" : "Check your inbox"}</h2>
              <p>
                {step === "email"
                  ? "Enter the email linked to MySend."
                  : "If the account exists, a code has been sent to that email."}
              </p>
            </header>

            {step === "code" && (
              <div className="auth-notice" role="status">
                Enter the code and choose a password with at least 10 characters.
              </div>
            )}
            {developmentCode && step === "code" && (
              <div className="development-code">
                Local password code: <strong>{developmentCode}</strong>
              </div>
            )}
            {error && <p className="auth-error" role="alert">{error}</p>}

            {step === "email" ? (
              <form className="auth-focused-form" onSubmit={requestCode}>
                <label className="text-field">
                  <span>Email</span>
                  <input
                    type="email"
                    required
                    maxLength={320}
                    autoComplete="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                  />
                </label>
                <button className="auth-submit" disabled={busy}>
                  {busy ? "Sending code" : "Send password code"}
                </button>
              </form>
            ) : (
              <form className="auth-focused-form" onSubmit={resetPassword}>
                <label className="code-field auth-code-field">
                  <span>Password code</span>
                  <input
                    value={code}
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    placeholder="000000"
                    onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
                  />
                </label>
                <label className="text-field">
                  <span>New password <small>10+ characters</small></span>
                  <input
                    type="password"
                    required
                    minLength={10}
                    maxLength={100}
                    autoComplete="new-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                  />
                </label>
                <button
                  className="auth-submit"
                  disabled={busy || code.length !== 6 || password.length < 10}
                >
                  {busy ? "Updating password" : "Set new password"}
                </button>
              </form>
            )}
          </div>
        </div>
      </section>
    </main>
  );
}

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : "Something went wrong.";
}
