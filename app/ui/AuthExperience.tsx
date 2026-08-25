"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Account, api } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

type AuthMode = "login" | "signup";
type RegisterStep = "details" | "code";

type VerificationResult = {
  expiresAt: string;
  delivered: boolean;
  developmentCode?: string | null;
};

export function AuthExperience({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const [step, setStep] = useState<RegisterStep>("details");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [developmentCode, setDevelopmentCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function submitLogin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api<Account>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      router.replace("/settings");
      router.refresh();
    } catch (caught) {
      setError(messageOf(caught));
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
          : "Local delivery is active. Use the code shown below.",
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
      await api<Account>("/api/auth/register/verify", {
        method: "POST",
        body: JSON.stringify({ email, code }),
      });
      router.replace("/settings");
      router.refresh();
    } catch (caught) {
      setError(messageOf(caught));
      setBusy(false);
    }
  }

  const isLogin = mode === "login";
  const isVerification = !isLogin && step === "code";

  return (
    <main className="auth-page">
      <SiteHeader />
      <section className="auth-layout" aria-labelledby="auth-title">
        <div className="auth-story">
          <p className="auth-context">Your MySend account</p>
          <h1 id="auth-title">
            {isLogin ? "Come back to your rooms." : "Keep the rooms you create."}
          </h1>
          <p className="auth-story-copy">
            {isLogin
              ? "Sign in to see active ShareRooms and use your account limits."
              : "Create one free account. We verify your email once, then your rooms stay easy to find."}
          </p>
          <div className="auth-promise">
            <p>Guest sharing never requires an account.</p>
            <Link href="/">Continue without logging in</Link>
          </div>
        </div>

        <div className="auth-panel">
          <div className="auth-form-wrap">
            {isVerification && (
              <button
                className="auth-back"
                type="button"
                onClick={() => {
                  setStep("details");
                  setCode("");
                  setNotice("");
                  setError("");
                }}
              >
                Back to account details
              </button>
            )}

            <header className="auth-form-heading">
              <h2>
                {isLogin
                  ? "Log in"
                  : isVerification
                    ? "Check your inbox"
                    : "Create your account"}
              </h2>
              <p>
                {isLogin
                  ? "Use the email and password linked to MySend."
                  : isVerification
                    ? `Enter the six-digit code sent to ${email}.`
                    : "Free accounts can keep track of two active ShareRooms."}
              </p>
            </header>

            {notice && <div className="auth-notice" role="status">{notice}</div>}
            {developmentCode && isVerification && (
              <div className="development-code">
                Local verification code: <strong>{developmentCode}</strong>
              </div>
            )}
            {error && <p className="auth-error" role="alert">{error}</p>}

            {isLogin ? (
              <form className="auth-focused-form" onSubmit={submitLogin}>
                <EmailField value={email} onChange={setEmail} />
                <PasswordField value={password} onChange={setPassword} login />
                <button className="auth-submit" disabled={busy}>
                  {busy ? "Logging in..." : "Log in"}
                </button>
              </form>
            ) : isVerification ? (
              <form className="auth-focused-form" onSubmit={verifyCode}>
                <label className="code-field auth-code-field">
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
                <button className="auth-submit" disabled={busy || code.length !== 6}>
                  {busy ? "Verifying..." : "Finish setup"}
                </button>
              </form>
            ) : (
              <form className="auth-focused-form" onSubmit={requestCode}>
                <EmailField value={email} onChange={setEmail} />
                <PasswordField value={password} onChange={setPassword} />
                <button className="auth-submit" disabled={busy}>
                  {busy ? "Sending code..." : "Send verification code"}
                </button>
              </form>
            )}

            {!isVerification && (
              <p className="auth-switch">
                {isLogin ? "New to MySend?" : "Already have an account?"}{" "}
                <Link href={isLogin ? "/signup" : "/login"}>
                  {isLogin ? "Create an account" : "Log in"}
                </Link>
              </p>
            )}
          </div>
        </div>
      </section>
    </main>
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

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : "Something went wrong.";
}
