"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Account, api, Room } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

type Mode = "create" | "join";

export function HomeExperience() {
  const router = useRouter();
  const [account, setAccount] = useState<Account | null>(null);
  const [mode, setMode] = useState<Mode>("create");
  const [visibility, setVisibility] = useState<"PUBLIC" | "PRIVATE">("PUBLIC");
  const [password, setPassword] = useState("");
  const [lifetime, setLifetime] = useState(15);
  const [accessLimit, setAccessLimit] = useState(20);
  const [code, setCode] = useState("");
  const [joinPassword, setJoinPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Account>("/api/auth/me").then(setAccount).catch(() => setAccount(null));
  }, []);

  const maximumMinutes = account?.roomMinutes ?? 15;
  const lifetimeOptions = [5, 10, 15, 30, 60, 120, 180].filter(
    (minutes) => minutes <= maximumMinutes,
  );
  const maximumEntries =
    account?.plan === "PREMIUM" ? 1000 : account ? 100 : 20;

  async function createRoom(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      const room = await api<Room>("/api/rooms", {
        method: "POST",
        body: JSON.stringify({
          visibility,
          password: visibility === "PRIVATE" ? password || null : null,
          lifetimeMinutes: lifetime,
          accessLimit,
        }),
      });
      router.push(`/room/${room.accessCode}`);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  async function joinRoom(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (!/^\d{4}[A-Z]$/.test(code)) {
      setError("Enter four digits followed by one letter.");
      return;
    }
    setBusy(true);
    try {
      const room = await api<Room>("/api/rooms/enter", {
        method: "POST",
        body: JSON.stringify({
          accessCode: code,
          password: joinPassword || null,
        }),
      });
      router.push(`/room/${room.accessCode}`);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="home-shell">
      <div className="home-top">
        <SiteHeader />
        <section className="hero" aria-labelledby="hero-title">
          <div className="hero-copy">
            <div className="eyebrow">
              <span className="pulse-dot" />
              Short-lived sharing, by design
            </div>
            <h1 id="hero-title">
              Send what
              <br />
              <em>you need.</em>
            </h1>
            <p>
              Open a room for text and files. Send one memorable code. When the
              timer ends, the room does too.
            </p>
            <div className="hero-notes" aria-label="Product highlights">
              <span>01 &nbsp; No app to install</span>
              <span>02 &nbsp; Case-insensitive codes</span>
              <span>03 &nbsp; You control the close</span>
            </div>
          </div>

          <div className="room-action-card">
            <div className="room-tabs" role="tablist" aria-label="Room action">
              <button
                type="button"
                role="tab"
                aria-selected={mode === "create"}
                className={mode === "create" ? "active" : ""}
                onClick={() => {
                  setMode("create");
                  setError("");
                }}
              >
                Create a room
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={mode === "join"}
                className={mode === "join" ? "active" : ""}
                onClick={() => {
                  setMode("join");
                  setError("");
                }}
              >
                Join with code
              </button>
            </div>

            {mode === "create" ? (
              <form className="room-form" onSubmit={createRoom}>
                <fieldset className="choice-field">
                  <legend>Who can enter?</legend>
                  <label className={visibility === "PUBLIC" ? "selected" : ""}>
                    <input
                      type="radio"
                      name="visibility"
                      value="PUBLIC"
                      checked={visibility === "PUBLIC"}
                      onChange={() => setVisibility("PUBLIC")}
                    />
                    <span>
                      <strong>Public</strong>
                      <small>Code only</small>
                    </span>
                  </label>
                  <label className={visibility === "PRIVATE" ? "selected" : ""}>
                    <input
                      type="radio"
                      name="visibility"
                      value="PRIVATE"
                      checked={visibility === "PRIVATE"}
                      onChange={() => setVisibility("PRIVATE")}
                    />
                    <span>
                      <strong>Private</strong>
                      <small>Optional password</small>
                    </span>
                  </label>
                </fieldset>

                {visibility === "PRIVATE" && (
                  <label className="text-field">
                    <span>Room password <small>optional</small></span>
                    <input
                      type="password"
                      value={password}
                      maxLength={100}
                      autoComplete="new-password"
                      placeholder="Add one more layer"
                      onChange={(event) => setPassword(event.target.value)}
                    />
                  </label>
                )}

                <div className="form-row">
                  <label className="text-field">
                    <span>Open for</span>
                    <select
                      value={lifetime}
                      onChange={(event) => setLifetime(Number(event.target.value))}
                    >
                      {lifetimeOptions.map((minutes) => (
                        <option value={minutes} key={minutes}>
                          {minutes} minutes
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="text-field">
                    <span>Guest entries</span>
                    <input
                      type="number"
                      min={1}
                      max={maximumEntries}
                      value={accessLimit}
                      onChange={(event) =>
                        setAccessLimit(Number(event.target.value))
                      }
                    />
                  </label>
                </div>

                {error && <p className="form-error" role="alert">{error}</p>}
                <button className="primary-action" type="submit" disabled={busy}>
                  {busy ? "Opening room…" : "Create ShareRoom"}
                  <span aria-hidden="true">↗</span>
                </button>
                <p className="form-footnote">
                  {account
                    ? `${account.plan === "PREMIUM" ? "Premium" : "Free"} account limits are active.`
                    : "Guest mode: no login, one room for up to 15 minutes."}
                </p>
              </form>
            ) : (
              <form className="room-form join-form" onSubmit={joinRoom}>
                <div className="join-intro">
                  <span className="join-index">05</span>
                  <div>
                    <h2>Five characters. That&apos;s it.</h2>
                    <p>Codes work in uppercase or lowercase.</p>
                  </div>
                </div>
                <label className="code-field">
                  <span>Access code</span>
                  <input
                    value={code}
                    inputMode="text"
                    autoCapitalize="characters"
                    autoComplete="off"
                    maxLength={5}
                    placeholder="4821K"
                    aria-label="Five-character access code"
                    onChange={(event) =>
                      setCode(
                        event.target.value
                          .toUpperCase()
                          .replace(/[^0-9A-Z]/g, "")
                          .slice(0, 5),
                      )
                    }
                  />
                </label>
                <label className="text-field">
                  <span>Room password <small>if required</small></span>
                  <input
                    type="password"
                    value={joinPassword}
                    maxLength={100}
                    autoComplete="current-password"
                    placeholder="Leave blank for public rooms"
                    onChange={(event) => setJoinPassword(event.target.value)}
                  />
                </label>
                {error && <p className="form-error" role="alert">{error}</p>}
                <button className="primary-action" type="submit" disabled={busy}>
                  {busy ? "Finding room…" : "Enter ShareRoom"}
                  <span aria-hidden="true">→</span>
                </button>
              </form>
            )}
          </div>
        </section>
      </div>

      <section className="how-section" id="how-it-works" aria-labelledby="how-title">
        <div className="section-kicker">One room, two useful surfaces</div>
        <div className="how-heading">
          <h2 id="how-title">Everything you need.<br />Nothing to organize.</h2>
          <p>
            MySend is deliberately temporary: a shared clipboard beside a clean
            file board, with the room controls always visible.
          </p>
        </div>
        <div className="feature-grid">
          <article className="feature-card feature-card--ink">
            <span className="feature-number">01</span>
            <div className="clipboard-demo">
              <div className="demo-top"><span>Clipboard</span><span>1,284 / 10,000</span></div>
              <p>Meeting link, shipping address, a code snippet — paste it once and pick it up anywhere.</p>
              <span className="text-cursor" />
            </div>
            <h3>A clipboard that travels</h3>
            <p>Copy, edit, and save up to your plan limit with conflict-safe updates.</p>
          </article>
          <article className="feature-card feature-card--paper">
            <span className="feature-number">02</span>
            <div className="file-demo">
              <div><b>Q3-notes.pdf</b><span>2.4 MB</span></div>
              <div><b>handoff.py</b><span>18 KB</span></div>
              <div><b>reference.png</b><span>4.1 MB</span></div>
            </div>
            <h3>A file board, not a maze</h3>
            <p>PDF, source code, documents, and images — ready to download without folders.</p>
          </article>
          <article className="feature-card feature-card--lime">
            <span className="feature-number">03</span>
            <div className="timer-demo">
              <small>ROOM 4821K CLOSES IN</small>
              <strong>42:18</strong>
              <div><span style={{ width: "68%" }} /></div>
            </div>
            <h3>Limits you can see</h3>
            <p>Time remaining, entries used, privacy, and storage stay in the corner — never hidden.</p>
          </article>
        </div>
      </section>

      <section className="plans-section" id="plans" aria-labelledby="plans-title">
        <div className="plans-copy">
          <span className="section-kicker">Simple membership</span>
          <h2 id="plans-title">Free for the quick handoff.<br />Premium for the working session.</h2>
        </div>
        <div className="plan-grid">
          <article className="plan-card">
            <div><span>Free</span><strong>$0</strong></div>
            <p>Register once to see My ShareRooms and raise every limit.</p>
            <ul>
              <li>Guest mode: no login, 15 minutes</li>
              <li>2 active rooms</li>
              <li>60 minute maximum</li>
              <li>10,000 character clipboard</li>
              <li>1 GB per room</li>
            </ul>
            <a href="#hero-title">Start free</a>
          </article>
          <article className="plan-card plan-card--premium">
            <div><span>Premium</span><strong>$9.99<small>/mo</small></strong></div>
            <p>For review sessions, workshops, and heavier handoffs.</p>
            <ul>
              <li>5 active rooms</li>
              <li>3 hour maximum</li>
              <li>100,000 character clipboard</li>
              <li>5 GB per room</li>
            </ul>
            <a href="/settings">See Premium</a>
          </article>
        </div>
      </section>

      <footer className="site-footer">
        <span>MySend</span>
        <p>Share what matters. Keep nothing longer than necessary.</p>
        <a href="/settings">Account &amp; settings</a>
      </footer>
    </main>
  );
}

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : "Something went wrong.";
}
