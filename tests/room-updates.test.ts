import assert from "node:assert/strict";
import test from "node:test";

type Listener = () => void;

class FakeEventTarget {
  private readonly listeners = new Map<string, Set<Listener>>();

  addEventListener(name: string, listener: Listener) {
    const listeners = this.listeners.get(name) ?? new Set<Listener>();
    listeners.add(listener);
    this.listeners.set(name, listeners);
  }

  removeEventListener(name: string, listener: Listener) {
    this.listeners.get(name)?.delete(listener);
  }

  dispatch(name: string) {
    this.listeners.get(name)?.forEach((listener) => listener());
  }

  count(name: string) {
    return this.listeners.get(name)?.size ?? 0;
  }
}

class FakeScheduler {
  callback: (() => void) | null = null;
  delay: number | null = null;
  cleared = false;

  setInterval(callback: () => void, delay: number) {
    this.callback = callback;
    this.delay = delay;
    return 7;
  }

  clearInterval(id: number) {
    assert.equal(id, 7);
    this.cleared = true;
    this.callback = null;
  }
}

async function loadRoomUpdates() {
  try {
    return await import("../app/lib/room-updates.ts");
  } catch (error) {
    assert.fail(`Room update monitoring is unavailable: ${String(error)}`);
  }
}

test("notifies only when the server has newer or unavailable room state", async () => {
  const { shouldNotifyRoomUpdate } = await loadRoomUpdates();

  assert.equal(shouldNotifyRoomUpdate(4, { version: 4, available: true }), false);
  assert.equal(shouldNotifyRoomUpdate(4, { version: 3, available: true }), false);
  assert.equal(shouldNotifyRoomUpdate(4, { version: 5, available: true }), true);
  assert.equal(shouldNotifyRoomUpdate(4, { version: 4, available: false }), true);
});

test("requires confirmation before replacing an unsaved clipboard draft", async () => {
  const { shouldConfirmClipboardReplacement } = await loadRoomUpdates();

  assert.equal(shouldConfirmClipboardReplacement("saved", "saved"), false);
  assert.equal(shouldConfirmClipboardReplacement("local draft", "saved"), true);
});

test("keeps clipboard edits made while a room refresh is in flight", async () => {
  const { resolveClipboardAfterRefresh } = await loadRoomUpdates();

  assert.equal(
    resolveClipboardAfterRefresh("draft after click", "draft before click", "remote"),
    "draft after click",
  );
  assert.equal(
    resolveClipboardAfterRefresh("draft before click", "draft before click", "remote"),
    "remote",
  );
  assert.equal(
    resolveClipboardAfterRefresh("local draft", "saved", "remote"),
    "local draft",
  );
});

test("retries a room snapshot when content changes between requests", async () => {
  const { loadConsistentRoomSnapshot } = await loadRoomUpdates();
  const rooms = [
    { version: 4, clipboardText: "old" },
    { version: 5, clipboardText: "latest" },
  ];
  const fileLists = [["old-file"], ["latest-file"]];
  const revisions = [
    { version: 5, available: true },
    { version: 5, available: true },
  ];
  let roomCalls = 0;
  let fileCalls = 0;
  let revisionCalls = 0;

  const snapshot = await loadConsistentRoomSnapshot({
    loadRoom: async () => rooms[roomCalls++],
    loadFiles: async () => fileLists[fileCalls++],
    loadRevision: async () => revisions[revisionCalls++],
  });

  assert.deepEqual(snapshot, {
    room: { version: 5, clipboardText: "latest" },
    files: ["latest-file"],
  });
  assert.equal(roomCalls, 2);
  assert.equal(fileCalls, 2);
  assert.equal(revisionCalls, 2);
});

test("checks visible rooms on an interval and when focus returns", async () => {
  const { startRoomUpdateMonitor } = await loadRoomUpdates();
  const scheduler = new FakeScheduler();
  const windowTarget = new FakeEventTarget();
  const documentTarget = new FakeEventTarget();
  let visible = true;
  let checks = 0;

  const stop = startRoomUpdateMonitor({
    check: async () => {
      checks += 1;
    },
    isVisible: () => visible,
    scheduler,
    windowTarget,
    documentTarget,
  });

  assert.equal(scheduler.delay, 30_000);
  scheduler.callback?.();
  await Promise.resolve();
  assert.equal(checks, 1);

  windowTarget.dispatch("focus");
  await Promise.resolve();
  assert.equal(checks, 2);

  visible = false;
  documentTarget.dispatch("visibilitychange");
  assert.equal(scheduler.cleared, true);

  stop();
  assert.equal(windowTarget.count("focus"), 0);
  assert.equal(documentTarget.count("visibilitychange"), 0);
});

test("resumes checking when a hidden room becomes visible", async () => {
  const { startRoomUpdateMonitor } = await loadRoomUpdates();
  const scheduler = new FakeScheduler();
  const windowTarget = new FakeEventTarget();
  const documentTarget = new FakeEventTarget();
  let visible = false;
  let checks = 0;

  const stop = startRoomUpdateMonitor({
    check: async () => {
      checks += 1;
    },
    isVisible: () => visible,
    scheduler,
    windowTarget,
    documentTarget,
  });

  assert.equal(scheduler.callback, null);
  visible = true;
  documentTarget.dispatch("visibilitychange");
  await Promise.resolve();
  assert.equal(checks, 1);
  assert.equal(scheduler.delay, 30_000);

  stop();
});

test("does not start another check while one is still running", async () => {
  const { startRoomUpdateMonitor } = await loadRoomUpdates();
  const scheduler = new FakeScheduler();
  const windowTarget = new FakeEventTarget();
  const documentTarget = new FakeEventTarget();
  let finishCheck: (() => void) | undefined;
  let checks = 0;

  const stop = startRoomUpdateMonitor({
    check: () => new Promise<void>((resolve) => {
      checks += 1;
      finishCheck = resolve;
    }),
    isVisible: () => true,
    scheduler,
    windowTarget,
    documentTarget,
  });

  scheduler.callback?.();
  windowTarget.dispatch("focus");
  assert.equal(checks, 1);

  finishCheck?.();
  await Promise.resolve();
  windowTarget.dispatch("focus");
  assert.equal(checks, 2);

  stop();
});
