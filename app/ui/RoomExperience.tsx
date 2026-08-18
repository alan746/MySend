"use client";

import {
  ChangeEvent,
  DragEvent,
  FormEvent,
  useEffect,
  useMemo,
  useState,
} from "react";
import Link from "next/link";
import { api, Room, RoomFile, roomDownloadUrl } from "../lib/api";
import { SiteHeader } from "./SiteHeader";

type RoomExperienceProps = {
  code: string;
};

export function RoomExperience({ code }: RoomExperienceProps) {
  const [room, setRoom] = useState<Room | null>(null);
  const [files, setFiles] = useState<RoomFile[]>([]);
  const [clipboard, setClipboard] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [needsEntry, setNeedsEntry] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [dragActive, setDragActive] = useState(false);
  const [copied, setCopied] = useState(false);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let active = true;

    async function loadRoom() {
      try {
        const [loaded, loadedFiles] = await Promise.all([
          api<Room>(`/api/rooms/${encodeURIComponent(code)}`),
          api<RoomFile[]>(`/api/rooms/${encodeURIComponent(code)}/files`),
        ]);
        if (!active) return;
        setRoom(loaded);
        setClipboard(loaded.clipboardText);
        setFiles(loadedFiles);
        setNeedsEntry(false);
      } catch (caught) {
        if (!active) return;
        setNeedsEntry(true);
        setError(messageOf(caught));
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadRoom();
    return () => {
      active = false;
    };
  }, [code]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  async function enterRoom(event: FormEvent) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const entered = await api<Room>("/api/rooms/enter", {
        method: "POST",
        body: JSON.stringify({ accessCode: code, password: password || null }),
      });
      setRoom(entered);
      setClipboard(entered.clipboardText);
      setNeedsEntry(false);
      const loadedFiles = await api<RoomFile[]>(
        `/api/rooms/${encodeURIComponent(code)}/files`,
      );
      setFiles(loadedFiles);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setLoading(false);
    }
  }

  async function saveClipboard() {
    if (!room) return;
    setError("");
    setSaving(true);
    try {
      const updated = await api<Room>(
        `/api/rooms/${encodeURIComponent(code)}/clipboard`,
        {
          method: "PATCH",
          body: JSON.stringify({ text: clipboard, version: room.version }),
        },
      );
      setRoom(updated);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setSaving(false);
    }
  }

  async function copyClipboard() {
    try {
      await navigator.clipboard.writeText(clipboard);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1400);
    } catch {
      setError("Clipboard access was blocked by the browser.");
    }
  }

  async function upload(file: File) {
    setError("");
    setUploading(true);
    const form = new FormData();
    form.set("file", file);
    try {
      const stored = await api<RoomFile>(
        `/api/rooms/${encodeURIComponent(code)}/files`,
        { method: "POST", body: form },
      );
      setFiles((current) => [stored, ...current]);
      const refreshed = await api<Room>(`/api/rooms/${encodeURIComponent(code)}`);
      setRoom(refreshed);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setUploading(false);
    }
  }

  async function deleteFile(fileId: string) {
    if (!window.confirm("Remove this file from the room?")) return;
    setError("");
    try {
      await api<void>(
        `/api/rooms/${encodeURIComponent(code)}/files/${encodeURIComponent(fileId)}`,
        { method: "DELETE" },
      );
      setFiles((current) => current.filter((file) => file.id !== fileId));
      const refreshed = await api<Room>(`/api/rooms/${encodeURIComponent(code)}`);
      setRoom(refreshed);
    } catch (caught) {
      setError(messageOf(caught));
    }
  }

  if (loading && !room) {
    return (
      <main className="room-page">
        <SiteHeader compact />
        <div className="room-loading" role="status">
          <span />
          Opening ShareRoom {code}…
        </div>
      </main>
    );
  }

  if (needsEntry || !room) {
    return (
      <main className="room-page">
        <SiteHeader compact />
        <section className="entry-gate">
          <span className="entry-code">{code}</span>
          <p className="section-kicker">ShareRoom entry</p>
          <h1>This room needs a quick check.</h1>
          <p>Enter the room password if the owner added one.</p>
          <form onSubmit={enterRoom}>
            <label className="text-field">
              <span>Password <small>if required</small></span>
              <input
                type="password"
                value={password}
                autoFocus
                maxLength={100}
                autoComplete="current-password"
                placeholder="Room password"
                onChange={(event) => setPassword(event.target.value)}
              />
            </label>
            {error && <p className="form-error" role="alert">{error}</p>}
            <button className="primary-action" disabled={loading}>
              {loading ? "Checking…" : "Enter ShareRoom"} <span>→</span>
            </button>
          </form>
          <Link href="/">Use another access code</Link>
        </section>
      </main>
    );
  }

  const remainingMs = Math.max(0, new Date(room.expiresAt).getTime() - now);
  const remaining = formatCountdown(remainingMs);
  const storagePercent = Math.min(
    100,
    room.fileLimitBytes ? (room.fileBytes / room.fileLimitBytes) * 100 : 0,
  );

  return (
    <main className="room-page">
      <SiteHeader compact />
      <section className="room-heading">
        <div>
          <p className="section-kicker">My ShareRoom</p>
          <h1>
            Room <span>{room.accessCode}</span>
          </h1>
          <button
            className="copy-code"
            type="button"
            onClick={() => navigator.clipboard.writeText(room.accessCode)}
          >
            Copy access code
          </button>
        </div>
        <div className="room-status-card">
          <span className="live-label"><i /> Live</span>
          <div>
            <small>Closes in</small>
            <strong>{remaining}</strong>
          </div>
          <dl>
            <div><dt>Privacy</dt><dd>{room.visibility.toLowerCase()}</dd></div>
            <div><dt>Entries</dt><dd>{room.accessCount} / {room.accessLimit}</dd></div>
            <div><dt>Plan</dt><dd>{room.plan.toLowerCase()}</dd></div>
          </dl>
        </div>
      </section>

      {error && (
        <div className="room-alert" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => setError("")}>Dismiss</button>
        </div>
      )}

      <section className="room-workspace" aria-label="ShareRoom workspace">
        <article className="workspace-panel clipboard-panel">
          <header>
            <div>
              <span className="panel-index">01</span>
              <h2>Clipboard</h2>
            </div>
            <button className="quiet-button" type="button" onClick={copyClipboard}>
              {copied ? "Copied" : "Copy all"}
            </button>
          </header>
          <textarea
            value={clipboard}
            maxLength={room.clipboardLimit}
            aria-label="Shared clipboard"
            placeholder="Paste text, links, notes, or a code snippet…"
            onChange={(event) => setClipboard(event.target.value)}
          />
          <footer>
            <span>
              {clipboard.length.toLocaleString()} / {room.clipboardLimit.toLocaleString()}
            </span>
            <button
              className="save-button"
              type="button"
              disabled={saving || clipboard === room.clipboardText}
              onClick={saveClipboard}
            >
              {saving ? "Saving…" : clipboard === room.clipboardText ? "Saved" : "Save changes"}
            </button>
          </footer>
        </article>

        <article className="workspace-panel files-panel">
          <header>
            <div>
              <span className="panel-index">02</span>
              <h2>File board</h2>
            </div>
            <span className="file-total">{files.length} files</span>
          </header>

          <label
            className={`drop-zone ${dragActive ? "is-dragging" : ""}`}
            onDragEnter={(event) => {
              event.preventDefault();
              setDragActive(true);
            }}
            onDragOver={(event) => event.preventDefault()}
            onDragLeave={() => setDragActive(false)}
            onDrop={(event: DragEvent<HTMLLabelElement>) => {
              event.preventDefault();
              setDragActive(false);
              const file = event.dataTransfer.files[0];
              if (file) void upload(file);
            }}
          >
            <input
              type="file"
              disabled={uploading}
              accept=".pdf,.txt,.md,.java,.py,.c,.h,.cpp,.hpp,.doc,.docx,.jpg,.jpeg,.png,.gif,.webp,.zip,.json"
              onChange={(event: ChangeEvent<HTMLInputElement>) => {
                const file = event.target.files?.[0];
                if (file) void upload(file);
                event.target.value = "";
              }}
            />
            <span className="upload-plus">+</span>
            <strong>{uploading ? "Uploading…" : "Drop a file or browse"}</strong>
            <small>PDF, code, docs, images, ZIP · up to {formatBytes(singleFileLimit(room.plan))}</small>
          </label>

          <div className="file-list">
            {files.length === 0 ? (
              <div className="empty-files">Files added to this room appear here.</div>
            ) : (
              files.map((file) => (
                <div className="file-row" key={file.id}>
                  <span className="file-extension">
                    {extensionOf(file.name)}
                  </span>
                  <div>
                    <strong>{file.name}</strong>
                    <small>{formatBytes(file.sizeBytes)} · {formatDate(file.uploadedAt)}</small>
                  </div>
                  <a href={roomDownloadUrl(code, file.id)}>Download</a>
                  {room.owner && (
                    <button
                      type="button"
                      aria-label={`Delete ${file.name}`}
                      onClick={() => void deleteFile(file.id)}
                    >
                      ×
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
          <footer className="storage-meter">
            <div><span style={{ width: `${storagePercent}%` }} /></div>
            <small>{formatBytes(room.fileBytes)} of {formatBytes(room.fileLimitBytes)} used</small>
          </footer>
        </article>
      </section>

      {room.owner && (
        <RoomSettings room={room} code={code} onRoomChange={setRoom} />
      )}
    </main>
  );
}

function RoomSettings({
  room,
  code,
  onRoomChange,
}: {
  room: Room;
  code: string;
  onRoomChange: (room: Room) => void;
}) {
  const [open, setOpen] = useState(false);
  const [visibility, setVisibility] = useState(room.visibility);
  const [password, setPassword] = useState("");
  const [accessLimit, setAccessLimit] = useState(room.accessLimit);
  const initialMinutes = Math.round(
    (new Date(room.expiresAt).getTime() - new Date(room.createdAt).getTime()) / 60_000,
  );
  const [lifetime, setLifetime] = useState(initialMinutes);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const maximumMinutes =
    room.plan === "PREMIUM" ? 180 : room.plan === "FREE" ? 60 : 15;
  const options = useMemo(
    () => [15, 30, 60, 120, 180].filter((value) => value <= maximumMinutes),
    [maximumMinutes],
  );

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const updated = await api<Room>(
        `/api/rooms/${encodeURIComponent(code)}/settings`,
        {
          method: "PATCH",
          body: JSON.stringify({
            visibility,
            password: password || null,
            lifetimeMinutes: lifetime,
            accessLimit,
            version: room.version,
          }),
        },
      );
      onRoomChange(updated);
      setPassword("");
      setOpen(false);
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  async function closeRoom() {
    if (!window.confirm("Close this room now? This cannot be undone.")) return;
    setBusy(true);
    try {
      await api<void>(`/api/rooms/${encodeURIComponent(code)}`, {
        method: "DELETE",
      });
      window.location.href = "/";
    } catch (caught) {
      setError(messageOf(caught));
      setBusy(false);
    }
  }

  return (
    <section className={`room-settings ${open ? "is-open" : ""}`}>
      <button
        className="settings-toggle"
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <span>Room controls</span>
        <small>Password, entries, privacy &amp; close time</small>
        <b>{open ? "−" : "+"}</b>
      </button>
      {open && (
        <form onSubmit={save}>
          <label className="text-field">
            <span>Privacy</span>
            <select
              value={visibility}
              onChange={(event) =>
                setVisibility(event.target.value as "PUBLIC" | "PRIVATE")
              }
            >
              <option value="PUBLIC">Public — code only</option>
              <option value="PRIVATE">Private</option>
            </select>
          </label>
          <label className="text-field">
            <span>Password <small>blank keeps the current password</small></span>
            <input
              type="password"
              value={password}
              disabled={visibility === "PUBLIC"}
              maxLength={100}
              placeholder={room.passwordProtected ? "Current password is set" : "Add a password"}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          <label className="text-field">
            <span>Guest entry limit</span>
            <input
              type="number"
              min={Math.max(1, room.accessCount)}
              max={room.plan === "PREMIUM" ? 1000 : room.plan === "FREE" ? 100 : 20}
              value={accessLimit}
              onChange={(event) => setAccessLimit(Number(event.target.value))}
            />
          </label>
          <label className="text-field">
            <span>Total lifetime</span>
            <select
              value={lifetime}
              onChange={(event) => setLifetime(Number(event.target.value))}
            >
              {options.map((minutes) => (
                <option value={minutes} key={minutes}>{minutes} minutes</option>
              ))}
            </select>
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <div className="settings-actions">
            <button className="danger-button" type="button" disabled={busy} onClick={closeRoom}>
              Close room now
            </button>
            <button className="primary-action" type="submit" disabled={busy}>
              {busy ? "Saving…" : "Save controls"}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

function formatCountdown(milliseconds: number) {
  const seconds = Math.floor(milliseconds / 1000);
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = seconds % 60;
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`
    : `${String(minutes).padStart(2, "0")}:${String(remainder).padStart(2, "0")}`;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1_048_576) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1_073_741_824) return `${(bytes / 1_048_576).toFixed(1)} MB`;
  return `${(bytes / 1_073_741_824).toFixed(1)} GB`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function extensionOf(name: string) {
  const extension = name.split(".").pop() || "file";
  return extension.slice(0, 4).toUpperCase();
}

function singleFileLimit(plan: Room["plan"]) {
  if (plan === "PREMIUM") return 1_073_741_824;
  if (plan === "FREE") return 262_144_000;
  return 52_428_800;
}

function messageOf(value: unknown) {
  return value instanceof Error ? value.message : "Something went wrong.";
}
