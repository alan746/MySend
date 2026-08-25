"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Account, api, Room } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

export function DashboardExperience() {
  const router = useRouter();
  const [account, setAccount] = useState<Account | null>(null);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [checking, setChecking] = useState(true);
  const [visibility, setVisibility] = useState<"PUBLIC" | "PRIVATE">("PUBLIC");
  const [roomPassword, setRoomPassword] = useState("");
  const [lifetime, setLifetime] = useState(15);
  const [accessLimit, setAccessLimit] = useState(20);
  const [code, setCode] = useState("");
  const [joinPassword, setJoinPassword] = useState("");
  const [createBusy, setCreateBusy] = useState(false);
  const [joinBusy, setJoinBusy] = useState(false);
  const [createError, setCreateError] = useState("");
  const [joinError, setJoinError] = useState("");

  useEffect(() => {
    let active = true;
    api<Account>("/api/auth/me")
      .then(async (current) => {
        if (!active) return;
        setAccount(current);
        setLifetime(Math.min(15, current.roomMinutes));
        setRooms(await api<Room[]>("/api/rooms"));
      })
      .catch(() => {
        if (active) setAccount(null);
      })
      .finally(() => {
        if (active) setChecking(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function createRoom(event: FormEvent) {
    event.preventDefault();
    setCreateError("");
    setCreateBusy(true);
    try {
      const room = await api<Room>("/api/rooms", {
        method: "POST",
        body: JSON.stringify({
          visibility,
          password: visibility === "PRIVATE" ? roomPassword || null : null,
          lifetimeMinutes: lifetime,
          accessLimit,
        }),
      });
      router.push(`/room/${room.accessCode}`);
    } catch (caught) {
      setCreateError(messageOf(caught));
      setCreateBusy(false);
    }
  }

  async function joinRoom(event: FormEvent) {
    event.preventDefault();
    setJoinError("");
    if (!/^\d{4}[A-Z]$/.test(code)) {
      setJoinError("Enter four digits followed by one letter.");
      return;
    }
    setJoinBusy(true);
    try {
      const room = await api<Room>("/api/rooms/enter", {
        method: "POST",
        body: JSON.stringify({ accessCode: code, password: joinPassword || null }),
      });
      router.push(`/room/${room.accessCode}`);
    } catch (caught) {
      setJoinError(messageOf(caught));
      setJoinBusy(false);
    }
  }

  if (checking) {
    return (
      <main className="dashboard-page">
        <SiteHeader compact />
        <section className="product-loading" role="status">
          <strong>Opening your dashboard</strong>
          <span>Checking active rooms and account limits.</span>
        </section>
      </main>
    );
  }

  if (!account) {
    return (
      <main className="dashboard-page">
        <SiteHeader compact />
        <section className="product-gate">
          <p className="section-kicker">Dashboard</p>
          <h1>Log in to see your rooms.</h1>
          <p>Your dashboard keeps the rooms you create in one place.</p>
          <div>
            <Link className="solid-link" href="/login">Log in</Link>
            <Link href="/">Continue as guest</Link>
          </div>
        </section>
      </main>
    );
  }

  const lifetimeOptions = [5, 10, 15, 30, 60, 120, 180].filter(
    (minutes) => minutes <= account.roomMinutes,
  );
  const maximumEntries = account.plan === "PREMIUM" ? 1000 : 100;

  return (
    <main className="dashboard-page">
      <SiteHeader compact />
      <section className="dashboard-heading">
        <div>
          <p className="section-kicker">Your dashboard</p>
          <h1>Share something.</h1>
          <p>Create a room or enter a code. Both actions stay one click away.</p>
        </div>
        <Link className="dashboard-account-link" href="/settings">
          <span>{account.email}</span>
          <strong>{account.plan === "PREMIUM" ? "Premium" : "Free"} membership</strong>
        </Link>
      </section>

      <section className="dashboard-actions" aria-label="ShareRoom actions">
        <form className="dashboard-action-card" onSubmit={createRoom}>
          <header>
            <span>01</span>
            <div>
              <h2>Create a ShareRoom</h2>
              <p>Open a temporary space for text and files.</p>
            </div>
          </header>

          <fieldset className="dashboard-choice">
            <legend>Access</legend>
            <label className={visibility === "PUBLIC" ? "selected" : ""}>
              <input
                type="radio"
                name="dashboard-visibility"
                checked={visibility === "PUBLIC"}
                onChange={() => setVisibility("PUBLIC")}
              />
              <span><strong>Public</strong><small>Code only</small></span>
            </label>
            <label className={visibility === "PRIVATE" ? "selected" : ""}>
              <input
                type="radio"
                name="dashboard-visibility"
                checked={visibility === "PRIVATE"}
                onChange={() => setVisibility("PRIVATE")}
              />
              <span><strong>Private</strong><small>Password optional</small></span>
            </label>
          </fieldset>

          {visibility === "PRIVATE" && (
            <label className="product-field product-field--full">
              <span>Room password <small>optional</small></span>
              <input
                type="password"
                maxLength={100}
                autoComplete="new-password"
                value={roomPassword}
                onChange={(event) => setRoomPassword(event.target.value)}
              />
            </label>
          )}

          <div className="dashboard-field-row">
            <label className="product-field">
              <span>Open for</span>
              <select value={lifetime} onChange={(event) => setLifetime(Number(event.target.value))}>
                {lifetimeOptions.map((minutes) => (
                  <option value={minutes} key={minutes}>{minutes} minutes</option>
                ))}
              </select>
            </label>
            <label className="product-field">
              <span>Guest entries</span>
              <input
                type="number"
                min={1}
                max={maximumEntries}
                value={accessLimit}
                onChange={(event) => setAccessLimit(Number(event.target.value))}
              />
            </label>
          </div>

          {createError && <p className="product-error" role="alert">{createError}</p>}
          <button className="dashboard-submit" type="submit" disabled={createBusy}>
            {createBusy ? "Creating room" : "Create ShareRoom"}
            <span aria-hidden="true">→</span>
          </button>
        </form>

        <form className="dashboard-action-card dashboard-action-card--join" onSubmit={joinRoom}>
          <header>
            <span>02</span>
            <div>
              <h2>Join a ShareRoom</h2>
              <p>Enter four digits followed by one letter.</p>
            </div>
          </header>

          <label className="dashboard-code-field">
            <span>Access code</span>
            <input
              value={code}
              inputMode="text"
              autoCapitalize="characters"
              autoComplete="off"
              maxLength={5}
              placeholder="4821K"
              onChange={(event) =>
                setCode(event.target.value.toUpperCase().replace(/[^0-9A-Z]/g, "").slice(0, 5))
              }
            />
            <small>Uppercase and lowercase both work.</small>
          </label>

          <label className="product-field product-field--full">
            <span>Room password <small>if required</small></span>
            <input
              type="password"
              maxLength={100}
              autoComplete="current-password"
              value={joinPassword}
              onChange={(event) => setJoinPassword(event.target.value)}
            />
          </label>

          {joinError && <p className="product-error" role="alert">{joinError}</p>}
          <button className="dashboard-submit" type="submit" disabled={joinBusy}>
            {joinBusy ? "Finding room" : "Enter ShareRoom"}
            <span aria-hidden="true">→</span>
          </button>
        </form>
      </section>

      <section className="dashboard-lower">
        <div className="dashboard-rooms">
          <header>
            <div>
              <p className="section-kicker">My ShareRooms</p>
              <h2>Active now</h2>
            </div>
            <span>{rooms.length} of {account.activeRoomLimit}</span>
          </header>
          {rooms.length === 0 ? (
            <div className="dashboard-empty">
              <strong>No active rooms yet.</strong>
              <p>Create one above and it will stay here until it closes.</p>
            </div>
          ) : (
            <div className="dashboard-room-list">
              {rooms.map((room) => (
                <Link href={`/room/${room.accessCode}`} key={room.id}>
                  <strong>{room.accessCode}</strong>
                  <span>{room.visibility === "PRIVATE" ? "Private" : "Public"}</span>
                  <span>{room.remainingEntries} entries left</span>
                  <time>{minutesLeft(room.expiresAt)} min left</time>
                  <b aria-hidden="true">→</b>
                </Link>
              ))}
            </div>
          )}
        </div>

        <aside className="dashboard-limits">
          <p className="section-kicker">Current limits</p>
          <dl>
            <div><dt>Room time</dt><dd>{account.roomMinutes} min</dd></div>
            <div><dt>Clipboard</dt><dd>{account.clipboardCharacters.toLocaleString()}</dd></div>
            <div><dt>File storage</dt><dd>{formatBytes(account.roomFileBytes)}</dd></div>
          </dl>
          <Link href="/settings">View membership details</Link>
        </aside>
      </section>
    </main>
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
